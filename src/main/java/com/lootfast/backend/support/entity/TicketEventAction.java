package com.lootfast.backend.support.entity;

/** Loại thao tác ghi vào lịch sử bất biến của ticket (FR-AD-04). */
public enum TicketEventAction {
  CREATED,
  STATUS_CHANGED,
  NOTE,
  RESOLVED,
  REJECTED,
  CLOSED
}
