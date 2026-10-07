ALTER TABLE premium_payment_receipts ADD COLUMN source VARCHAR(16) NOT NULL DEFAULT 'IPN' CHECK(source IN ('IPN','QUERYDR'));
CREATE TABLE premium_reconciliation_jobs (
 order_id UUID PRIMARY KEY REFERENCES premium_orders(id) ON DELETE CASCADE,
 state VARCHAR(24) NOT NULL DEFAULT 'WAITING' CHECK(state IN ('WAITING','PROCESSING','DONE','EXHAUSTED','MANUAL_REVIEW')),
 attempts INT NOT NULL DEFAULT 0 CHECK(attempts BETWEEN 0 AND 12),
 next_at TIMESTAMPTZ NOT NULL, deadline TIMESTAMPTZ NOT NULL,
 last_claim_at TIMESTAMPTZ, last_request_at TIMESTAMPTZ, lease_token UUID, lease_until TIMESTAMPTZ,
 last_result VARCHAR(40),
 CHECK((state='PROCESSING' AND lease_token IS NOT NULL AND lease_until IS NOT NULL) OR (state<>'PROCESSING' AND lease_token IS NULL AND lease_until IS NULL))
);
CREATE INDEX idx_premium_query_due ON premium_reconciliation_jobs(next_at,order_id) WHERE state IN ('WAITING','PROCESSING');
CREATE TABLE premium_query_runs (
 request_id UUID PRIMARY KEY, order_id UUID NOT NULL REFERENCES premium_orders(id) ON DELETE CASCADE,
 started_at TIMESTAMPTZ NOT NULL, completed_at TIMESTAMPTZ, result VARCHAR(40), provider_code VARCHAR(2),
 response_id VARCHAR(32)
);
CREATE INDEX idx_premium_query_history ON premium_query_runs(order_id,started_at DESC);
INSERT INTO premium_reconciliation_jobs(order_id,next_at,deadline)
 SELECT a.order_id,a.expires_at+interval '5 minutes',a.created_at+interval '48 hours'
 FROM premium_payment_attempts a JOIN premium_orders o ON o.id=a.order_id WHERE o.status='PENDING_PAYMENT';
