# Notification email outbox

Deal and order notification email requests are now persisted in `email_outbox`
inside the transaction that creates the notification. These listeners are synchronous
`@EventListener`s: an enqueue failure rolls back the operation rather than silently
losing the request. OTP delivery keeps its existing dedicated async executor.

## Worker

- Poll every 5 seconds using a dedicated scheduler so network calls do not block
  pickup reminders or the no-show sweeper.
- Handle at most 50 jobs per sweep; claim one job immediately before sending it.
- Short PostgreSQL transaction uses `FOR UPDATE OF e SKIP LOCKED`.
- Each claim increments the attempt counter and stores a fresh ownership token.
- Lease length is configured Brevo connect timeout + read timeout + 60 seconds.
- Commit claim, then call Brevo synchronously without holding a database transaction.
- Acknowledge using both ID and ownership token, before the lease expires.
- Expired claims can be recovered after a worker crash. Late workers cannot overwrite
  results belonging to a new claim.
- Check email/category preferences again at claim time; disabled requests become
  `CANCELLED`. This cannot recall a request already sent to the provider.

## Delivery states

`PENDING -> PROCESSING -> SENT`, or `PROCESSING -> PENDING` for retry.
Permanent provider errors and exhausted attempts become `FAILED`.
Five attempts maximum, including claims lost to crashes. Retry delays start at
30 seconds, 2 minutes, 10 minutes, and 30 minutes, with up to 20% jitter.
HTTP 408/429/5xx, transport failures and invalid provider responses are retryable.
Numeric Retry-After is honored up to 6 hours. Other HTTP errors are permanent.
The same UUID idempotency key is reused on every attempt; a structured Brevo
idempotency duplicate response counts as already accepted.

`SENT` means Brevo accepted the request, not that it reached the user's inbox.
This is at-least-once processing, not guaranteed exactly-once external delivery.
A crash after provider acceptance but before acknowledgment can trigger another
attempt. Brevo's deduplication window is limited:
https://developers.brevo.com/docs/heterogenous-versions-batch-emails
Real provider behavior must be verified in the deployed account before claiming
strong duplicate-delivery guarantees. Tests mock Brevo; they do not send real mail.

## Operations

No new environment keys or Maven dependencies are required.
`lootfast.email-outbox.enabled=false` disables the worker in isolated tests.
The worker starts by default with the application. Flyway migration V28 is required.

Inspect without exposing recipient addresses or HTML content:

```sql
SELECT status, count(*) FROM email_outbox GROUP BY status;
SELECT id, status, attempt_count, next_attempt_at, locked_until, last_error_code
FROM email_outbox WHERE status = 'FAILED' ORDER BY created_at;
```

Micrometer meters: `lootfast.email.outbox.pending`,
`lootfast.email.outbox.results` (outcome tag), `lootfast.email.outbox.errors`.
Use the existing monitoring configuration to expose/collect them; this feature does
not expose an additional public administrative endpoint.

FAILED jobs remain available for investigation. There is no automatic manual-replay
API or automatic retention purge. Establish retention/archival for terminal email
payloads before operating at significant volume. Deleting a notification cascades
to its outbox row, including recipient and message snapshots.

Capacity has not been load-tested. One sending thread per backend instance and
bounded sweeps limit local concurrency, but throughput depends on Brevo latency,
account quotas and PostgreSQL capacity. Match expected traffic and monitor backlog
before adding more worker instances.

## Validation

Version matrix observed in this checkout: Java 21.0.12, Maven Wrapper 3.9.16,
Spring Boot 3.5.16, Flyway 11.20.3, PostgreSQL JDBC 42.7.11,
Testcontainers 1.21.4, Redis 7 (unchanged).
New outbox tests use PostgreSQL 17.11; existing PostgreSQL 18.6 tests also pass.
No existing PostgreSQL volume was migrated, downgraded or deleted.


Integration tests use PostgreSQL 17, matching Docker Compose. Existing PostgreSQL
18 tests also validate the forward migration. Coverage includes transaction rollback,
unique notification enqueue, concurrent claim, SKIP LOCKED, lease recovery, stale
acknowledgment, retry timing/limits, preference cancellation, cascade deletion and
sending outside a database transaction. Existing OTP and notification tests remain.

The feature branch is based on the lifecycle-notification commit on
`feature/trust-and-impact`. Merge that prerequisite first, then target this branch
at dev (or review this as a stacked PR against the prerequisite branch).
