-- Merchant-prefixed indexes cannot serve global date-range reports efficiently.
CREATE INDEX idx_orders_created_system_report ON customer_orders (created_at);
CREATE INDEX idx_orders_completed_system_report ON customer_orders (finalized_at, merchant_profile_id)
    WHERE status = 'COMPLETED';
