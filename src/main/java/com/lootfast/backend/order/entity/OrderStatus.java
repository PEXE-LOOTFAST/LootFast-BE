package com.lootfast.backend.order.entity;

import java.util.Set;

/**
 * Vòng đời đơn hàng. Mọi đơn bắt đầu ở {@link #RESERVED} và chỉ được chuyển sang đúng một trong bốn
 * trạng thái cuối theo bảng {@link #allowedNext} bên dưới. Đây là nguồn chân lý duy nhất cho quy tắc
 * chuyển trạng thái — mọi thay đổi trạng thái phải đi qua {@link CustomerOrder#transition}.
 */
public enum OrderStatus {
  /** Đã giữ hàng, chờ khách đến nhận. Trạng thái khởi tạo duy nhất. */
  RESERVED,
  /** Cửa hàng đã xác nhận giao (task 057). Trạng thái cuối. */
  COMPLETED,
  /** Khách tự huỷ trước giờ nhận 30 phút (task 034). Trạng thái cuối. */
  CANCELLED,
  /** Khách không đến lấy sau khi hết giờ nhận 30 phút (task 037). Trạng thái cuối. */
  NO_SHOW,
  /** Cửa hàng huỷ đơn. Trạng thái cuối. */
  MERCHANT_CANCELLED;

  static {
    RESERVED.allowedNext = Set.of(COMPLETED, CANCELLED, NO_SHOW, MERCHANT_CANCELLED);
    COMPLETED.allowedNext = Set.of();
    CANCELLED.allowedNext = Set.of();
    NO_SHOW.allowedNext = Set.of();
    MERCHANT_CANCELLED.allowedNext = Set.of();
  }

  private Set<OrderStatus> allowedNext;

  /** True nếu được phép chuyển thẳng từ trạng thái này sang {@code next}. */
  public boolean canTransitionTo(OrderStatus next) {
    return allowedNext.contains(next);
  }

  /** True nếu là trạng thái cuối, không còn chuyển đi đâu được nữa. */
  public boolean isTerminal() {
    return allowedNext.isEmpty();
  }
}
