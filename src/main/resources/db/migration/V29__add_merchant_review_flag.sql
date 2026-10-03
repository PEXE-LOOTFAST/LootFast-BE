-- BR-MER-06 / FR-TR-02: khi một cửa hàng bị ≥3 báo "hết hàng" trong 30 ngày, đánh dấu để Admin
-- rà soát. Điểm uy tín được tính on-the-fly (không lưu), chỉ cờ rà soát này cần lưu lại.
ALTER TABLE merchant_profiles
    ADD COLUMN review_flagged_at TIMESTAMPTZ;
