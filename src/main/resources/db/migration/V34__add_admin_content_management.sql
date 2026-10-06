CREATE TABLE content_categories (
    code VARCHAR(100) PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    enabled BOOLEAN NOT NULL,
    sort_order INTEGER NOT NULL CHECK (sort_order BETWEEN 0 AND 10000),
    revision BIGINT NOT NULL DEFAULT 0 CHECK (revision >= 0),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by UUID
);
CREATE INDEX idx_content_categories_public ON content_categories(enabled, sort_order, code);
INSERT INTO content_categories(code, name, enabled, sort_order, updated_at) VALUES
    ('BAKERY', 'Bánh và đồ nướng', TRUE, 10, CURRENT_TIMESTAMP),
    ('MEAL', 'Món ăn', TRUE, 20, CURRENT_TIMESTAMP),
    ('VEGETARIAN', 'Món chay', TRUE, 30, CURRENT_TIMESTAMP),
    ('DRINK', 'Đồ uống', TRUE, 40, CURRENT_TIMESTAMP),
    ('OTHER', 'Khác', TRUE, 50, CURRENT_TIMESTAMP);
-- Preserve all existing category references without modifying historical records.
INSERT INTO content_categories(code, name, enabled, sort_order, updated_at)
SELECT DISTINCT UPPER(TRIM(category_code)), UPPER(TRIM(category_code)), TRUE, 100, CURRENT_TIMESTAMP
FROM (
    SELECT category_code FROM deals
    UNION SELECT category_code FROM deal_templates
    UNION SELECT category_code FROM user_profile_favorite_categories
) existing
WHERE TRIM(category_code) <> ''
ON CONFLICT (code) DO NOTHING;

CREATE TABLE content_banners (
    id UUID PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    alt_text VARCHAR(200) NOT NULL,
    target_path VARCHAR(255) NOT NULL,
    image_url VARCHAR(2048),
    storage_key VARCHAR(255),
    enabled BOOLEAN NOT NULL,
    sort_order INTEGER NOT NULL CHECK (sort_order BETWEEN 0 AND 10000),
    starts_at TIMESTAMP WITH TIME ZONE,
    ends_at TIMESTAMP WITH TIME ZONE,
    revision BIGINT NOT NULL DEFAULT 0 CHECK (revision >= 0),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by UUID NOT NULL,
    CONSTRAINT ck_banner_image_pair CHECK ((image_url IS NULL) = (storage_key IS NULL)),
    CONSTRAINT ck_banner_enabled_image CHECK (NOT enabled OR image_url IS NOT NULL),
    CONSTRAINT ck_banner_schedule CHECK (
        (starts_at IS NULL AND ends_at IS NULL) OR
        (starts_at IS NOT NULL AND ends_at IS NOT NULL AND starts_at < ends_at)
    )
);
CREATE INDEX idx_content_banners_public ON content_banners(enabled, sort_order, id);

CREATE TABLE content_legal_versions (
    id UUID PRIMARY KEY,
    document VARCHAR(32) NOT NULL CHECK (document IN ('TERMS_OF_USE', 'PRIVACY_POLICY')),
    document_version VARCHAR(50) NOT NULL,
    title VARCHAR(200) NOT NULL,
    body TEXT NOT NULL CHECK (CHAR_LENGTH(body) BETWEEN 1 AND 50000),
    published_at TIMESTAMP WITH TIME ZONE,
    revision BIGINT NOT NULL DEFAULT 0 CHECK (revision >= 0),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by UUID NOT NULL,
    CONSTRAINT uk_legal_document_version UNIQUE(document, document_version)
);
CREATE TABLE content_legal_heads (
    document VARCHAR(32) PRIMARY KEY CHECK (document IN ('TERMS_OF_USE', 'PRIVACY_POLICY')),
    current_id UUID REFERENCES content_legal_versions(id)
);
INSERT INTO content_legal_heads(document) VALUES ('TERMS_OF_USE'), ('PRIVACY_POLICY');
