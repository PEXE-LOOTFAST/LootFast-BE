-- Admin trial grants only; paid subscription activation is a separate future use case.
CREATE TABLE premium_entitlements (
    id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    benefit VARCHAR(64) NOT NULL CHECK (benefit='MERCHANT_ADVANCED_REPORTS'),
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL,
    granted_by UUID NOT NULL,
    grant_reason VARCHAR(500) NOT NULL,
    revoked_at TIMESTAMPTZ,
    revoked_by UUID,
    revoke_reason VARCHAR(500),
    CONSTRAINT ck_premium_grant_window CHECK (starts_at < ends_at),
    CONSTRAINT ck_premium_grant_reason CHECK (LENGTH(TRIM(grant_reason)) > 0),
    CONSTRAINT ck_premium_revoke_metadata CHECK (
        (revoked_at IS NULL AND revoked_by IS NULL AND revoke_reason IS NULL)
        OR (revoked_at IS NOT NULL AND revoked_by IS NOT NULL AND revoke_reason IS NOT NULL AND LENGTH(TRIM(revoke_reason)) > 0)
    )
);
CREATE INDEX idx_premium_entitlement_active
    ON premium_entitlements(user_id, benefit, ends_at, starts_at) WHERE revoked_at IS NULL;
CREATE INDEX idx_premium_entitlement_history
    ON premium_entitlements(user_id, granted_at DESC, id);
