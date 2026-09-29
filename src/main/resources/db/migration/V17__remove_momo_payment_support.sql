-- MoMo support was removed before production onboarding. Keep this as a forward-only
-- migration because V16 may already have been applied to developer or shared databases.
ALTER TABLE customer_orders
    DROP CONSTRAINT IF EXISTS customer_orders_payment_method_check;

ALTER TABLE customer_orders
    ADD CONSTRAINT customer_orders_payment_method_check
    CHECK (payment_method IN ('BANK_TRANSFER', 'CASH'));

DROP TABLE IF EXISTS momo_payments;
