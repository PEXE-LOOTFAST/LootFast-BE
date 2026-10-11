package com.lootfast.backend.order.dto;

import com.lootfast.backend.order.entity.PaymentMethod;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public record CreateOrderRequest(
    @NotNull UUID dealId,
    @Min(1) @Max(5) int quantity,
    @NotNull PaymentMethod paymentMethod,
    // Khoảng giờ khách hẹn đến lấy; bắt buộc qua API, phải nằm trong khung pickup của deal.
    @NotNull Instant pickupFrom,
    @NotNull Instant pickupTo) {

  // Tiện ích cho lời gọi nội bộ/test không chọn khoảng giờ: để null, service mặc định cả khung
  // pickup của deal.
  public CreateOrderRequest(UUID dealId, int quantity, PaymentMethod paymentMethod) {
    this(dealId, quantity, paymentMethod, null, null);
  }
}
