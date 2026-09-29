package com.lootfast.backend.order.dto;

import com.lootfast.backend.order.entity.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderResponse(
    UUID orderId,
    UUID dealId,
    UUID storeId,
    String dealName,
    int quantity,
    BigDecimal unitPrice,
    BigDecimal totalAmount,
    Instant pickupStart,
    Instant pickupEnd,
    OrderStatus orderStatus,
    PaymentMethod paymentMethod,
    PaymentStatus paymentStatus,
    String paymentReference,
    Instant createdAt,
    Instant finalizedAt) {
  public static OrderResponse from(CustomerOrder o) {
    return new OrderResponse(
        o.getPublicId(),
        o.getDeal().getPublicId(),
        o.getMerchant().getUser().getPublicId(),
        o.getDealName(),
        o.getQuantity(),
        o.getUnitPrice(),
        o.getTotalAmount(),
        o.getPickupStart(),
        o.getPickupEnd(),
        o.getStatus(),
        o.getPaymentMethod(),
        o.getPaymentStatus(),
        o.getPaymentReference(),
        o.getCreatedAt(),
        o.getFinalizedAt());
  }
}
