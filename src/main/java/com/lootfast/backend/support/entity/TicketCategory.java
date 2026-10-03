package com.lootfast.backend.support.entity;

/** Phân loại nội dung ticket hỗ trợ/tranh chấp. */
public enum TicketCategory {
  /** Tranh chấp thanh toán (khách báo đã chuyển nhưng chưa được xác nhận). Dùng cho 045. */
  PAYMENT_DISPUTE,
  OUT_OF_STOCK,
  WRONG_DESCRIPTION,
  CLOSED_EARLY,
  OTHER
}
