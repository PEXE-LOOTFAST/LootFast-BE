-- Accounts always start as BUYER. Merchant access is represented exclusively
-- by the additive MERCHANT role granted after license approval.
ALTER TABLE users DROP COLUMN account_type;
