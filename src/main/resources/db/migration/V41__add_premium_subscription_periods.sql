CREATE TABLE premium_subscriptions (
 id UUID PRIMARY KEY, user_id BIGINT NOT NULL REFERENCES users(id),
 audience VARCHAR(16) NOT NULL CHECK(audience IN ('BUYER','MERCHANT')),
 created_at TIMESTAMPTZ NOT NULL, UNIQUE(user_id,audience)
);
CREATE TABLE premium_subscription_periods (
 id UUID PRIMARY KEY, subscription_id UUID NOT NULL REFERENCES premium_subscriptions(id),
 purchase_reference UUID NOT NULL UNIQUE,
 plan_revision_id UUID NOT NULL REFERENCES premium_plan_revisions(id),
 plan_version INTEGER NOT NULL CHECK(plan_version>0), plan_code VARCHAR(64) NOT NULL,
 plan_name VARCHAR(120) NOT NULL, price_vnd BIGINT NOT NULL CHECK(price_vnd BETWEEN 1000 AND 999999999),
 duration_days INTEGER NOT NULL CHECK(duration_days BETWEEN 1 AND 366),
 terms_document_id UUID NOT NULL REFERENCES content_legal_versions(id), terms_version VARCHAR(50) NOT NULL,
 confirmed_at TIMESTAMPTZ NOT NULL, starts_at TIMESTAMPTZ NOT NULL, ends_at TIMESTAMPTZ NOT NULL,
 created_at TIMESTAMPTZ NOT NULL,
 CHECK(starts_at<ends_at), CHECK(ends_at=starts_at+duration_days*INTERVAL '24 hours'),
 CHECK(confirmed_at<=created_at), UNIQUE(subscription_id,starts_at)
);
CREATE INDEX idx_premium_period_history ON premium_subscription_periods(subscription_id,starts_at DESC,id);
ALTER TABLE premium_entitlements ADD COLUMN source VARCHAR(16) NOT NULL DEFAULT 'MANUAL';
ALTER TABLE premium_entitlements ADD COLUMN subscription_period_id UUID REFERENCES premium_subscription_periods(id);
ALTER TABLE premium_entitlements ALTER COLUMN granted_by DROP NOT NULL;
ALTER TABLE premium_entitlements ADD CONSTRAINT ck_premium_entitlement_source CHECK (
 (source='MANUAL' AND subscription_period_id IS NULL AND granted_by IS NOT NULL)
 OR (source='PURCHASE' AND subscription_period_id IS NOT NULL AND granted_by IS NULL)
);
ALTER TABLE premium_entitlements ADD CONSTRAINT uk_premium_period_benefit UNIQUE(subscription_period_id,benefit);
