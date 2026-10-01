-- A pickup credential is separate from the bank-transfer reference. It is consumed when the
-- merchant moves an order from RESERVED to COMPLETED.
ALTER TABLE customer_orders ADD COLUMN pickup_code VARCHAR(9);

-- Backfill existing orders deterministically in base 36. Future orders use random
-- six-character codes in application code.
CREATE FUNCTION lootfast_order_id_base36(input_value BIGINT) RETURNS TEXT
LANGUAGE plpgsql
IMMUTABLE
AS $$
DECLARE
    alphabet CONSTANT TEXT := '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ';
    remaining BIGINT := input_value;
    encoded TEXT := '';
BEGIN
    IF remaining < 0 OR remaining >= 2176782336 THEN
        RAISE EXCEPTION 'Order id % cannot fit in a six-character pickup code', input_value;
    END IF;

    IF remaining = 0 THEN
        RETURN '0';
    END IF;

    WHILE remaining > 0 LOOP
        encoded := SUBSTRING(alphabet FROM ((remaining % 36)::INTEGER + 1) FOR 1) || encoded;
        remaining := remaining / 36;
    END LOOP;

    RETURN encoded;
END;
$$;

UPDATE customer_orders
SET pickup_code = 'LF-' || LPAD(lootfast_order_id_base36(id), 6, '0');

DROP FUNCTION lootfast_order_id_base36(BIGINT);

ALTER TABLE customer_orders
    ALTER COLUMN pickup_code SET NOT NULL,
    ADD CONSTRAINT uk_orders_pickup_code UNIQUE (pickup_code),
    ADD CONSTRAINT chk_orders_pickup_code_format
        CHECK (pickup_code ~ '^LF-[0-9A-Z]{6}$');

-- Supports the merchant's primary order-list query and its stable pickup-time pagination.
CREATE INDEX idx_orders_merchant_pickup_start
    ON customer_orders(merchant_profile_id, pickup_start, id);
