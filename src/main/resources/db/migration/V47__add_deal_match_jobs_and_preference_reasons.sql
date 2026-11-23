-- Matching-deal notifications are computed by a background worker instead of inside the
-- merchant's publish transaction. One job row per published deal; the worker walks buyers in
-- user-id order (keyset cursor = last_user_id) and retries with backoff on failure.
CREATE TABLE deal_match_jobs (
    deal_id BIGINT PRIMARY KEY REFERENCES deals(id) ON DELETE CASCADE,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    last_user_id BIGINT NOT NULL DEFAULT 0 CHECK (last_user_id >= 0),
    failure_count INTEGER NOT NULL DEFAULT 0 CHECK (failure_count BETWEEN 0 AND 5),
    completed_at TIMESTAMPTZ,
    failed_at TIMESTAMPTZ,
    last_error_code VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_deal_match_jobs_pending ON deal_match_jobs (next_attempt_at, deal_id)
    WHERE completed_at IS NULL AND failed_at IS NULL;

-- Food-preference reasons: an explicit favourite category is now reported separately from a
-- favourited dish, and repeated completed orders in a category count as an implicit preference.
ALTER TABLE notifications DROP CONSTRAINT ck_notifications_match_reason;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_match_reason CHECK (
    match_reason IS NULL OR match_reason IN (
        'FAVORITE_STORE', 'FAVORITE_ITEM', 'FAVORITE_CATEGORY', 'ORDER_HISTORY',
        'NEARBY_AREA', 'BIG_DISCOUNT', 'SAVED_FILTER'
    )
);

-- The daily cap lookup reuses idx_notifications_recipient_created (recipient_user_id, created_at).
