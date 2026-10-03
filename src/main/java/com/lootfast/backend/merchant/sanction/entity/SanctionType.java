package com.lootfast.backend.merchant.sanction.entity;

/** Loại xử phạt Admin áp lên cửa hàng (FR-TR-05). */
public enum SanctionType {
  /** Cảnh cáo: ghi nhận lý do, cửa hàng vẫn hoạt động bình thường. */
  WARNING,
  /** Đình chỉ: ẩn khỏi feed và chặn tạo deal mới tới hết thời hạn. */
  SUSPENSION
}
