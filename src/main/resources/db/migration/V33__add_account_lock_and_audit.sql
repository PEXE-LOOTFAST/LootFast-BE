ALTER TABLE users ADD COLUMN locked_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN lock_reason VARCHAR(500);
ALTER TABLE users ADD COLUMN authentication_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE users ADD CONSTRAINT chk_user_lock_metadata CHECK (
    (locked_at IS NULL AND lock_reason IS NULL)
    OR (locked_at IS NOT NULL AND lock_reason IS NOT NULL));
ALTER TABLE users ADD CONSTRAINT chk_user_auth_version CHECK(authentication_version >= 0);
CREATE INDEX idx_users_admin_created ON users(created_at DESC,id DESC);
CREATE INDEX idx_users_locked_created ON users(created_at DESC,id DESC) WHERE locked_at IS NOT NULL;
CREATE TABLE account_admin_audit (
    id UUID PRIMARY KEY,
    target_public_id UUID NOT NULL,
    actor_public_id UUID NOT NULL,
    action VARCHAR(8) NOT NULL CHECK(action IN ('LOCK','UNLOCK')),
    reason VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_account_audit_target ON account_admin_audit(target_public_id,created_at DESC,id DESC);
