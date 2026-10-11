package com.lootfast.backend.order.dto;

import com.lootfast.backend.order.entity.CustomerOrder;
import com.lootfast.backend.order.entity.OrderStatus;
import com.lootfast.backend.order.entity.PaymentMethod;
import com.lootfast.backend.order.entity.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MerchantOrderResponse(
    UUID orderId,
    String dealName,
    int quantity,
    BigDecimal totalAmount,
    Instant pickupStart,
    Instant pickupEnd,
    Instant pickupFrom,
    Instant pickupTo,
    OrderStatus orderStatus,
    PaymentMethod paymentMethod,
    PaymentStatus paymentStatus,
    String paymentReference,
    Instant paymentReportedAt,
    Instant paymentConfirmedAt,
    Instant createdAt,
    Instant finalizedAt) {

  public static MerchantOrderResponse from(CustomerOrder order) {
    return new MerchantOrderResponse(
        order.getPublicId(),
        order.getDealName(),
        order.getQuantity(),
        order.getTotalAmount(),
        order.getPickupStart(),
        order.getPickupEnd(),
        order.getPickupFrom(),
        order.getPickupTo(),
        order.getStatus(),
        order.getPaymentMethod(),
        order.getPaymentStatus(),
        order.getPaymentReference(),
        order.getPaymentReportedAt(),
        order.getPaymentConfirmedAt(),
        order.getCreatedAt(),
        order.getFinalizedAt());
  }
}
