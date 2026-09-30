-- Snapshot giá gốc từng phần tại thời điểm đặt để tính "số tiền tiết kiệm" (FR-CU-11).
-- Không join sang deals khi hiển thị: đơn phải sống độc lập khi deal bị gỡ (BR-ORD-10)
-- và không đổi theo giá deal sửa về sau.
ALTER TABLE customer_orders
    ADD COLUMN original_unit_price NUMERIC(12, 2);

-- Backfill đơn cũ từ giá gốc hiện tại của deal tương ứng.
UPDATE customer_orders o
SET original_unit_price = d.original_price
FROM deals d
WHERE o.deal_id = d.id;

ALTER TABLE customer_orders
    ALTER COLUMN original_unit_price SET NOT NULL;

-- Giá gốc không bao giờ nhỏ hơn giá đã chốt của đơn — lớp phòng thủ cuối.
ALTER TABLE customer_orders
    ADD CONSTRAINT chk_orders_original_ge_unit
        CHECK (original_unit_price >= unit_price);
