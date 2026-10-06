CREATE INDEX idx_orders_merchant_created_report
    ON customer_orders (merchant_profile_id, created_at);
CREATE INDEX idx_orders_merchant_completed_report
    ON customer_orders (merchant_profile_id, finalized_at)
    WHERE status = 'COMPLETED';
