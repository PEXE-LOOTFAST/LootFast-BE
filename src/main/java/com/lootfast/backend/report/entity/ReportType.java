package com.lootfast.backend.report.entity;

/** Loại vấn đề khách báo cáo về cửa hàng (FR-CU-15). */
public enum ReportType {
  /** Đến nơi thì cửa hàng báo hết phần đã giữ. Dùng cho trigger rà soát uy tín (068). */
  OUT_OF_STOCK,
  /** Món nhận khác với mô tả/ảnh của deal. */
  WRONG_DESCRIPTION,
  /** Cửa hàng đóng cửa sớm trước giờ nhận. */
  CLOSED_EARLY,
  /** Vấn đề khác, nêu trong mô tả. */
  OTHER
}
