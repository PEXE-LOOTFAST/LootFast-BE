package com.lootfast.backend.notification.outbox;

import com.lootfast.backend.common.email.EmailDeliveryResult;
import com.lootfast.backend.notification.repository.NotificationPreferenceRepository;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailOutboxStore {
  public static final int MAX_ATTEMPTS = 5;
  private final JdbcTemplate jdbc;
  private final NotificationPreferenceRepository preferences;
  private final com.lootfast.backend.buyer.savedfilter.notification.SavedFilterRecipientRepository
      matching;
  private final Clock clock;

  public EmailOutboxStore(
      JdbcTemplate jdbc,
      NotificationPreferenceRepository preferences,
      Clock clock,
      com.lootfast.backend.buyer.savedfilter.notification.SavedFilterRecipientRepository matching) {
    this.jdbc = jdbc;
    this.preferences = preferences;
    this.clock = clock;
    this.matching = matching;
  }

  @Transactional(timeout = 5)
  public Optional<ClaimedEmail> claim(Duration lease) {
    Instant now = clock.instant();
    for (int skipped = 0; skipped < 50; skipped++) {
      var rows =
          jdbc.query(
              """
              select e.id, e.recipient_email, e.subject, e.html_body, e.attempt_count,
                     e.preference_category, n.recipient_user_id,n.deal_public_id
              from email_outbox e join notifications n on n.public_id = e.notification_id
              where (e.status = 'PENDING' and e.next_attempt_at <= ?)
                 or (e.status = 'PROCESSING' and e.locked_until <= ?)
              order by e.next_attempt_at, e.created_at, e.id
              limit 1 for update of e skip locked
              """,
              (rs, index) ->
                  new Candidate(
                      rs.getObject("id", UUID.class),
                      rs.getString("recipient_email"),
                      rs.getString("subject"),
                      rs.getString("html_body"),
                      rs.getInt("attempt_count"),
                      EmailPreferenceCategory.valueOf(rs.getString("preference_category")),
                      rs.getLong("recipient_user_id"),
                      rs.getObject("deal_public_id", UUID.class)),
              Timestamp.from(now),
              Timestamp.from(now));
      if (rows.isEmpty()) return Optional.empty();
      var row = rows.getFirst();
      if (!enabled(row)) {
        jdbc.update(
            "update email_outbox set status='CANCELLED', locked_until=null, lock_token=null,"
                + " last_error_code='PREFERENCES_DISABLED' where id=?",
            row.id());
        continue;
      }
      if (row.attempt() >= MAX_ATTEMPTS) {
        jdbc.update(
            "update email_outbox set status='FAILED', locked_until=null, lock_token=null,"
                + " last_error_code='ATTEMPTS_EXHAUSTED' where id=?",
            row.id());
        continue;
      }
      UUID token = UUID.randomUUID();
      jdbc.update(
          "update email_outbox set status='PROCESSING', attempt_count=attempt_count+1,"
              + " locked_until=?, lock_token=? where id=?",
          Timestamp.from(now.plus(lease)),
          token,
          row.id());
      return Optional.of(
          new ClaimedEmail(
              row.id(), token, row.email(), row.subject(), row.body(), row.attempt() + 1));
    }
    return Optional.empty();
  }

  private boolean enabled(Candidate row) {
    if (!Boolean.TRUE.equals(
        jdbc.queryForObject(
            "select exists(select 1 from users where id=? and locked_at is null and"
                + " status='VERIFIED')",
            Boolean.class,
            row.userId()))) return false;
    var preference = preferences.findByUserId(row.userId()).orElse(null);
    if (preference == null || !preference.isEmailEnabled()) return false;
    return switch (row.category()) {
      case SAVED_FILTER_DEALS ->
          preference.isMatchingDealsEnabled()
              && matching.emailEligible(row.userId(), row.dealId(), clock.instant());
      case MATCHING_DEALS -> preference.isMatchingDealsEnabled();
      case BUYER_ORDER_UPDATES -> preference.isBuyerOrderUpdatesEnabled();
      case MERCHANT_ORDER_UPDATES -> preference.isMerchantOrderUpdatesEnabled();
      case PICKUP_REMINDERS -> preference.isPickupRemindersEnabled();
    };
  }

  @Transactional(timeout = 5)
  public boolean finish(ClaimedEmail job, EmailDeliveryResult result) {
    Instant now = clock.instant();
    String status;
    if (result.outcome() == EmailDeliveryResult.Outcome.ACCEPTED) status = "SENT";
    else if (result.outcome() == EmailDeliveryResult.Outcome.PERMANENT_FAILURE
        || job.attempt() >= MAX_ATTEMPTS) status = "FAILED";
    else status = "PENDING";
    long[] delays = {30, 120, 600, 1800, 3600};
    long seconds = delays[Math.min(job.attempt() - 1, delays.length - 1)];
    Duration delay =
        Duration.ofSeconds(
            seconds + java.util.concurrent.ThreadLocalRandom.current().nextLong(seconds / 5 + 1));
    if (result.retryAfter() != null && result.retryAfter().compareTo(delay) > 0)
      delay = result.retryAfter();
    return jdbc.update(
            """
            update email_outbox set status=?, next_attempt_at=?, sent_at=?,
                locked_until=null, lock_token=null, last_error_code=?
            where id=? and status='PROCESSING' and lock_token=? and locked_until > ?
            """,
            status,
            Timestamp.from(now.plus(delay)),
            "SENT".equals(status) ? Timestamp.from(now) : null,
            result.errorCode(),
            job.id(),
            job.token(),
            Timestamp.from(now))
        == 1;
  }

  @Transactional(readOnly = true)
  public long pendingCount() {
    return jdbc.queryForObject(
        "select (select count(*) from email_outbox where status='PENDING') + (select count(*) from"
            + " email_outbox where status='PROCESSING')",
        Long.class);
  }

  private record Candidate(
      UUID id,
      String email,
      String subject,
      String body,
      int attempt,
      EmailPreferenceCategory category,
      Long userId,
      UUID dealId) {}
}
