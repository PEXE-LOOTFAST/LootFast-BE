-- Forward port of the unmerged notification outbox; existing migrations stay immutable.
CREATE TABLE email_outbox (
    id UUID PRIMARY KEY,
    notification_id UUID NOT NULL UNIQUE REFERENCES notifications(public_id) ON DELETE CASCADE,
    recipient_email VARCHAR(320) NOT NULL,
    subject VARCHAR(200) NOT NULL,
    html_body TEXT NOT NULL,
    preference_category VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    locked_until TIMESTAMPTZ,
    lock_token UUID,
    created_at TIMESTAMPTZ NOT NULL,
    sent_at TIMESTAMPTZ,
    last_error_code VARCHAR(64),
    CONSTRAINT ck_email_outbox_status CHECK (status IN ('PENDING','PROCESSING','SENT','FAILED','CANCELLED')),
    CONSTRAINT ck_email_outbox_attempts CHECK (attempt_count BETWEEN 0 AND 5),
    CONSTRAINT ck_email_outbox_category CHECK (preference_category IN (
        'MATCHING_DEALS','SAVED_FILTER_DEALS','BUYER_ORDER_UPDATES','MERCHANT_ORDER_UPDATES','PICKUP_REMINDERS')),
    CONSTRAINT ck_email_outbox_lock CHECK (
        (status='PROCESSING' AND locked_until IS NOT NULL AND lock_token IS NOT NULL)
        OR (status<>'PROCESSING' AND locked_until IS NULL AND lock_token IS NULL))
);
CREATE INDEX idx_email_outbox_pending ON email_outbox (next_attempt_at, created_at, id) WHERE status='PENDING';
CREATE INDEX idx_email_outbox_expired ON email_outbox (locked_until) WHERE status='PROCESSING';
