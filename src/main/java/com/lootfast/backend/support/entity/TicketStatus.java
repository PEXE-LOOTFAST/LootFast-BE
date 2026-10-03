package com.lootfast.backend.support.entity;

import java.util.Set;

/**
 * Vòng đời ticket. Nguồn chân lý duy nhất cho chuyển trạng thái — mọi thay đổi đi qua {@link
 * SupportTicket}. Admin xử lý ở task 070; task 069 chỉ tạo ticket ở {@link #OPEN}.
 */
public enum TicketStatus {
  OPEN,
  IN_REVIEW,
  RESOLVED,
  REJECTED,
  CLOSED;

  static {
    OPEN.allowedNext = Set.of(IN_REVIEW, RESOLVED, REJECTED);
    IN_REVIEW.allowedNext = Set.of(RESOLVED, REJECTED);
    RESOLVED.allowedNext = Set.of(CLOSED);
    REJECTED.allowedNext = Set.of(CLOSED);
    CLOSED.allowedNext = Set.of();
  }

  private Set<TicketStatus> allowedNext;

  public boolean canTransitionTo(TicketStatus next) {
    return allowedNext.contains(next);
  }

  public boolean isTerminal() {
    return allowedNext.isEmpty();
  }
}
