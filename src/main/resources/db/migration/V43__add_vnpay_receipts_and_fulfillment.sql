ALTER TABLE premium_subscription_periods ADD COLUMN premium_order_id UUID UNIQUE REFERENCES premium_orders(id);
ALTER TABLE premium_subscription_periods ADD CONSTRAINT ck_period_order_reference CHECK(premium_order_id IS NULL OR premium_order_id=purchase_reference);
CREATE TABLE premium_provider_transactions (
 merchant_code VARCHAR(8) NOT NULL, transaction_no VARCHAR(15) NOT NULL,
 order_id UUID NOT NULL REFERENCES premium_orders(id), created_at TIMESTAMPTZ NOT NULL,
 PRIMARY KEY(merchant_code,transaction_no)
);
CREATE INDEX idx_provider_transaction_order ON premium_provider_transactions(order_id);
CREATE TABLE premium_payment_receipts (
 id UUID PRIMARY KEY, order_id UUID NOT NULL REFERENCES premium_orders(id),
 fingerprint VARCHAR(64) NOT NULL, merchant_code VARCHAR(8) NOT NULL,
 transaction_no VARCHAR(15) NOT NULL, amount_scaled BIGINT NOT NULL CHECK(amount_scaled>=0),
 response_code VARCHAR(2) NOT NULL, transaction_status VARCHAR(2) NOT NULL,
 bank_code VARCHAR(20) NOT NULL, paid_at TIMESTAMPTZ, received_at TIMESTAMPTZ NOT NULL,
 outcome VARCHAR(32) NOT NULL CHECK(outcome IN ('WAITING','FULFILLED','FAILED','REVIEW_REQUIRED','AMOUNT_MISMATCH','IGNORED')),
 period_id UUID UNIQUE REFERENCES premium_subscription_periods(id),
 CHECK((outcome='FULFILLED' AND period_id IS NOT NULL) OR (outcome<>'FULFILLED' AND period_id IS NULL)),
 UNIQUE(order_id,fingerprint)
);
CREATE UNIQUE INDEX uk_receipt_fulfilled_order ON premium_payment_receipts(order_id) WHERE outcome='FULFILLED';
CREATE INDEX idx_receipt_review ON premium_payment_receipts(received_at,id) WHERE outcome IN ('WAITING','REVIEW_REQUIRED');
ALTER TABLE premium_order_events ADD COLUMN receipt_id UUID REFERENCES premium_payment_receipts(id);
ALTER TABLE premium_order_events DROP CONSTRAINT premium_order_events_event_type_check;
ALTER TABLE premium_order_events ADD CONSTRAINT premium_order_events_event_type_check CHECK(event_type IN ('CREATED','PAYMENT_INITIATED','FULFILLED','PAYMENT_FAILED','REVIEW_REQUIRED','AMOUNT_MISMATCH','CALLBACK_IGNORED'));
