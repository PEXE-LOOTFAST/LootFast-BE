ALTER TABLE premium_entitlements DROP CONSTRAINT premium_entitlements_benefit_check;
ALTER TABLE premium_entitlements ADD CONSTRAINT premium_entitlements_benefit_check
    CHECK (benefit IN ('MERCHANT_ADVANCED_REPORTS','BUYER_SAVED_FILTERS'));

CREATE TABLE buyer_saved_filters (
    id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(80) NOT NULL CHECK (length(trim(name)) > 0),
    latitude NUMERIC(9,6) NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude NUMERIC(9,6) NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    radius_km NUMERIC(5,2) NOT NULL CHECK (radius_km BETWEEN 0.1 AND 50),
    keyword VARCHAR(100),
    category_code VARCHAR(100),
    max_price NUMERIC(12,2) CHECK (max_price BETWEEN 0 AND 9999999999.99),
    min_discount_percent INTEGER CHECK (min_discount_percent BETWEEN 0 AND 100),
    pickup_from TIME, pickup_to TIME,
    revision BIGINT NOT NULL DEFAULT 0 CHECK (revision >= 0),
    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
    CHECK ((pickup_from IS NULL AND pickup_to IS NULL)
        OR (pickup_from IS NOT NULL AND pickup_to IS NOT NULL AND pickup_from < pickup_to))
);
CREATE INDEX idx_buyer_saved_filters_owner ON buyer_saved_filters(user_id,created_at DESC,id);
-- The maximum of ten filters is serialized by locking the owning users row.
-- Categories are snapshots: disabling a category must not delete a saved filter.
