# Order reservations and payments

This document describes the backend contract for tasks 026–030.

## Reservation rules

Create a reservation with `POST /api/v1/orders` and an authenticated buyer JWT.
Every request must include a UUID in the `Idempotency-Key` header. Repeating the
same key and body within 24 hours returns the original response without reducing
stock again. Reusing the key for a different body, or after its replay window,
returns a conflict.

The database transaction locks the buyer, merchant, and deal in that order. This
serializes competing reservations and bank-account edits, so stock cannot become
negative and an order always keeps a consistent payment snapshot. A temporary
lock timeout returns `RESOURCE_BUSY`; the client should retry with the same
idempotency key.

A reservation is accepted only when all of these rules pass:

- the buyer is verified and has the `BUYER` role;
- the deal is active and inside its sale window;
- the current time is earlier than 15 minutes before pickup;
- the store uses one sale window and one pickup window for that local day;
- the buyer has fewer than three `RESERVED` orders;
- the buyer's `RESERVED` quantity for this deal, including the request, is at
  most five;
- enough stock remains.

The one-window rule is also expected to be checked when deal write APIs are
introduced. The reservation check protects the current read-only deal flow and
detects conflicting imported data.

## Payment methods

An order chooses one of `BANK_TRANSFER` or `CASH`.

`GET /api/v1/orders/{orderId}/payment-instructions` returns the current payment
state. For bank transfer it includes the store's bank details, exact amount,
unique order reference, and a VietQR image URL. The bank details and reference
remain the fallback when a QR image cannot be generated or loaded. Bank details
are copied into the order at reservation time, and a store cannot change its
bank account while it has a reserved order.

The VietQR image is a transfer instruction. It does not confirm payment by
itself. The production payment adapter must receive a signed bank/provider
webhook, verify the order reference, amount, destination account, and unique
transaction reference, then update the order. Duplicate callbacks must be
idempotent; mismatches go to `REVIEW_REQUIRED`.

For cash, the buyer pays the store at pickup. The backend never accepts a
frontend claim that a bank or cash payment succeeded.

## Operational notes

- Run the forward-only migrations through `V17__remove_momo_payment_support.sql`.
- Use the same PostgreSQL major version in deployment and integration tests.
- Keep the bank account snapshot on each order so later merchant edits cannot
  redirect an existing payment.
- Configure the public HTTPS webhook URL before enabling automatic bank
  confirmation in production.
- Monitor `RESOURCE_BUSY`, payment timeouts, unmatched transactions, and
  `REVIEW_REQUIRED` rates.
- Capacity under production traffic remains to be established with a
  representative load test and production-like database resources.
