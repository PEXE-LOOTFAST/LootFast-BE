CREATE TABLE premium_orders (
 id UUID PRIMARY KEY,
 user_id BIGINT NOT NULL REFERENCES users(id),
 idempotency_key UUID NOT NULL,
 plan_revision_id UUID NOT NULL REFERENCES premium_plan_revisions(id),
 audience VARCHAR(16) NOT NULL CHECK(audience IN ('BUYER','MERCHANT')),
 terms_document_id UUID NOT NULL REFERENCES content_legal_versions(id),
 plan_snapshot JSONB NOT NULL CHECK(jsonb_typeof(plan_snapshot)='object'),
 status VARCHAR(32) NOT NULL CHECK(status IN ('PENDING_PAYMENT','FULFILLED','FAILED','CANCELLED','EXPIRED','REVIEW_REQUIRED')),
 accepted_at TIMESTAMPTZ NOT NULL, created_at TIMESTAMPTZ NOT NULL, expires_at TIMESTAMPTZ NOT NULL,
 CHECK(accepted_at=created_at), CHECK(expires_at>created_at), UNIQUE(user_id,idempotency_key)
);
CREATE INDEX idx_premium_orders_owner_history ON premium_orders(user_id,created_at DESC,id);
CREATE INDEX idx_premium_orders_pending ON premium_orders(user_id,audience,expires_at) WHERE status='PENDING_PAYMENT';
CREATE TABLE premium_payment_attempts (
 id UUID PRIMARY KEY, order_id UUID NOT NULL UNIQUE REFERENCES premium_orders(id),
 reference VARCHAR(100) NOT NULL UNIQUE,
 status VARCHAR(32) NOT NULL CHECK(status IN ('PENDING','SUCCEEDED','FAILED','REVIEW_REQUIRED')),
 payment_url TEXT NOT NULL CHECK(length(payment_url)<=4096),
 created_at TIMESTAMPTZ NOT NULL, expires_at TIMESTAMPTZ NOT NULL CHECK(expires_at>created_at)
);
CREATE TABLE premium_order_events (
 id UUID PRIMARY KEY, order_id UUID NOT NULL REFERENCES premium_orders(id),
 event_type VARCHAR(32) NOT NULL CHECK(event_type IN ('CREATED','PAYMENT_INITIATED')),
 created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_premium_order_events ON premium_order_events(order_id,created_at,id);
