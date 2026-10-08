ALTER TABLE buyer_saved_filters ADD COLUMN notifications_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE buyer_saved_filters ADD COLUMN notifications_enabled_at TIMESTAMPTZ;
ALTER TABLE buyer_saved_filters ADD COLUMN search_keyword VARCHAR(100);
ALTER TABLE buyer_saved_filters ADD CONSTRAINT ck_filter_notifications_enabled CHECK (
    (notifications_enabled AND notifications_enabled_at IS NOT NULL)
    OR (NOT notifications_enabled AND notifications_enabled_at IS NULL));
UPDATE buyer_saved_filters SET search_keyword=trim(translate(lower(keyword),
    'áàảãạăắằẳẵặâấầẩẫậéèẻẽẹêếềểễệíìỉĩịóòỏõọôốồổỗộơớờởỡợúùủũụưứừửữựýỳỷỹỵđ',
    'aaaaaaaaaaaaaaaaaeeeeeeeeeeeiiiiiooooooooooooooooouuuuuuuuuuuyyyyyd')) WHERE keyword IS NOT NULL;
CREATE INDEX idx_saved_filter_alert_owner ON buyer_saved_filters(user_id,notifications_enabled_at)
    WHERE notifications_enabled;

CREATE TABLE premium_deal_notification_jobs (
    deal_id BIGINT PRIMARY KEY REFERENCES deals(id) ON DELETE CASCADE,
    event_at TIMESTAMPTZ NOT NULL,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    last_user_id BIGINT NOT NULL DEFAULT 0 CHECK (last_user_id>=0),
    completed_at TIMESTAMPTZ,
    failure_count INTEGER NOT NULL DEFAULT 0 CHECK (failure_count BETWEEN 0 AND 5),
    failed_at TIMESTAMPTZ,
    last_error_code VARCHAR(64)
);
CREATE INDEX idx_premium_deal_jobs_pending ON premium_deal_notification_jobs(next_attempt_at,deal_id)
    WHERE completed_at IS NULL AND failed_at IS NULL;
ALTER TABLE notifications DROP CONSTRAINT ck_notifications_match_reason;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_match_reason CHECK (
    match_reason IS NULL OR match_reason IN ('FAVORITE_STORE','FAVORITE_ITEM','NEARBY_AREA','BIG_DISCOUNT','SAVED_FILTER'));

CREATE INDEX idx_premium_saved_filter_recipient_window ON premium_entitlements(user_id,starts_at,ends_at)
    WHERE revoked_at IS NULL AND benefit='BUYER_SAVED_FILTERS';
