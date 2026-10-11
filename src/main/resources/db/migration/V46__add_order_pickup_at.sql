-- Khách hẹn một khoảng giờ đến lấy nằm trong khung pickup của deal. Cột pickup_start/pickup_end
-- vẫn là khung pickup của deal; pickup_from/pickup_to là khoảng khách cam kết đến và là căn cứ
-- cho huỷ và no-show (no-show kích hoạt 30 phút sau pickup_to).
ALTER TABLE customer_orders
    ADD COLUMN pickup_from TIMESTAMPTZ,
    ADD COLUMN pickup_to TIMESTAMPTZ;

-- Đơn cũ chưa chọn khoảng riêng: mặc định bằng cả khung pickup của deal đã lưu.
UPDATE customer_orders
SET pickup_from = pickup_start, pickup_to = pickup_end
WHERE pickup_from IS NULL OR pickup_to IS NULL;

ALTER TABLE customer_orders
    ALTER COLUMN pickup_from SET NOT NULL,
    ALTER COLUMN pickup_to SET NOT NULL,
    ADD CONSTRAINT chk_orders_pickup_slot
        CHECK (pickup_from < pickup_to
               AND pickup_from >= pickup_start
               AND pickup_to <= pickup_end);
