package com.lootfast.backend.report.entity;

/** Vòng đời xử lý một báo cáo. Khách tạo ra ở {@link #OPEN}; Admin xử lý sau (nhóm xử lý sau). */
public enum ReportStatus {
  /** Mới tạo, chờ xem xét. */
  OPEN,
  /** Admin đã xem và ghi nhận. */
  REVIEWED,
  /** Admin xác định không hợp lệ. */
  DISMISSED
}
