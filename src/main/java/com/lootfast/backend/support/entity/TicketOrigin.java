package com.lootfast.backend.support.entity;

/** Ai mở ticket. */
public enum TicketOrigin {
  BUYER,
  MERCHANT,
  /** Hệ thống tự mở (ví dụ chuyển khoản treo 24h — task 045). */
  SYSTEM
}
