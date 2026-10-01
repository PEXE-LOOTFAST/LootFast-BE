ALTER TABLE notifications ADD COLUMN order_public_id UUID;
ALTER TABLE notifications ADD COLUMN occurrence_key UUID;
ALTER TABLE notifications DROP CONSTRAINT uk_notifications_recipient_type_deal;
CREATE UNIQUE INDEX uk_notifications_recipient_type_deal
    ON notifications (recipient_user_id, type, deal_public_id) WHERE type = 'MATCHING_DEAL';
ALTER TABLE notifications ADD CONSTRAINT uk_notifications_recipient_type_order
    UNIQUE (recipient_user_id, type, order_public_id, occurrence_key);
ALTER TABLE notifications DROP CONSTRAINT ck_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_type CHECK (type IN (
    'MATCHING_DEAL', 'ORDER_RESERVED', 'TRANSFER_REPORTED', 'PAYMENT_CONFIRMED',
    'PAYMENT_REJECTED', 'ORDER_CANCELLED', 'ORDER_COMPLETED', 'ORDER_NO_SHOW', 'PICKUP_REMINDER'
));
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_target CHECK (
    (type = 'MATCHING_DEAL' AND order_public_id IS NULL AND match_reason IS NOT NULL)
    OR (type <> 'MATCHING_DEAL' AND order_public_id IS NOT NULL AND occurrence_key IS NOT NULL AND match_reason IS NULL)
);
CREATE INDEX idx_orders_reserved_pickup_reminders ON customer_orders (pickup_start, id)
    WHERE status = 'RESERVED';
