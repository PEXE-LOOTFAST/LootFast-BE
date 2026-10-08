CREATE TABLE premium_plans (
 id UUID PRIMARY KEY, code VARCHAR(64) NOT NULL UNIQUE CHECK(code ~ '^[A-Z0-9_]{1,64}$'),
 audience VARCHAR(16) NOT NULL CHECK(audience IN ('BUYER','MERCHANT')),
 created_at TIMESTAMPTZ NOT NULL, created_by UUID NOT NULL
);
CREATE TABLE premium_plan_revisions (
 id UUID PRIMARY KEY, plan_id UUID NOT NULL REFERENCES premium_plans(id),
 version INTEGER NOT NULL CHECK(version>0), revision BIGINT NOT NULL DEFAULT 0 CHECK(revision>=0),
 status VARCHAR(16) NOT NULL CHECK(status IN ('DRAFT','PUBLISHED','ARCHIVED')),
 name VARCHAR(120) NOT NULL CHECK(length(trim(name))>0),
 description VARCHAR(2000) NOT NULL CHECK(length(trim(description))>0),
 price_vnd BIGINT NOT NULL CHECK(price_vnd BETWEEN 1000 AND 999999999),
 duration_days INTEGER NOT NULL CHECK(duration_days BETWEEN 1 AND 366),
 terms_document_id UUID NOT NULL REFERENCES content_legal_versions(id),
 published_at TIMESTAMPTZ, updated_at TIMESTAMPTZ NOT NULL, updated_by UUID NOT NULL,
 UNIQUE(plan_id,version),
 CHECK(status<>'PUBLISHED' OR published_at IS NOT NULL),
 CHECK(status<>'DRAFT' OR published_at IS NULL)
);
CREATE UNIQUE INDEX uk_premium_plan_published ON premium_plan_revisions(plan_id) WHERE status='PUBLISHED';
CREATE UNIQUE INDEX uk_premium_plan_draft ON premium_plan_revisions(plan_id) WHERE status='DRAFT';
CREATE INDEX idx_premium_plan_catalog ON premium_plans(audience,code);
CREATE TABLE premium_plan_benefits (
 plan_revision_id UUID NOT NULL REFERENCES premium_plan_revisions(id),
 benefit VARCHAR(64) NOT NULL CHECK(benefit IN ('BUYER_SAVED_FILTERS','MERCHANT_ADVANCED_REPORTS')),
 PRIMARY KEY(plan_revision_id,benefit)
);
CREATE TABLE premium_plan_audits (
 id UUID PRIMARY KEY, plan_revision_id UUID NOT NULL REFERENCES premium_plan_revisions(id),
 actor_id UUID NOT NULL, action VARCHAR(16) NOT NULL CHECK(action IN ('CREATE','EDIT','PUBLISH','ARCHIVE','AUTO_ARCHIVE')),
 reason VARCHAR(500) NOT NULL CHECK(length(trim(reason))>0),
 snapshot JSONB NOT NULL CHECK(jsonb_typeof(snapshot)='object'), created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_premium_plan_audit ON premium_plan_audits(plan_revision_id,created_at DESC,id);
