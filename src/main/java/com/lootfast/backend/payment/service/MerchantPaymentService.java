package com.lootfast.backend.payment.service;

import com.lootfast.backend.notification.service.OrderNotificationService;
import com.lootfast.backend.notification.entity.NotificationType;

import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.config.DatabaseLockTimeout;
import com.lootfast.backend.merchant.repository.MerchantProfileRepository;
import com.lootfast.backend.order.dto.MerchantOrderResponse;
import com.lootfast.backend.order.entity.CustomerOrder;
import com.lootfast.backend.order.entity.OrderStatus;
import com.lootfast.backend.order.entity.PaymentMethod;
import com.lootfast.backend.order.entity.PaymentStatus;
import com.lootfast.backend.order.repository.OrderRepository;
import com.lootfast.backend.order.service.OrderService;
import com.lootfast.backend.payment.dto.RejectPaymentRequest;
import com.lootfast.backend.payment.entity.PaymentEvent;
import com.lootfast.backend.payment.entity.PaymentEventType;
import com.lootfast.backend.payment.repository.PaymentEventRepository;
import com.lootfast.backend.user.entity.User;
import com.lootfast.backend.user.entity.UserRole;
import com.lootfast.backend.user.entity.UserStatus;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MerchantPaymentService {
  private final MerchantProfileRepository merchants;
  private final OrderRepository orders;
  private final PaymentEventRepository events;
  private final DatabaseLockTimeout lockTimeout;
  private final Clock clock;
  private final OrderNotificationService notifications;

  public MerchantPaymentService(
      MerchantProfileRepository merchants,
      OrderRepository orders,
      PaymentEventRepository events,
      DatabaseLockTimeout lockTimeout,
      Clock clock,
      OrderNotificationService notifications) {
    this.merchants = merchants;
    this.orders = orders;
    this.events = events;
    this.lockTimeout = lockTimeout;
    this.clock = clock;
    this.notifications = notifications;
  }

  @Transactional(timeout = 5)
  public MerchantOrderResponse confirmBankTransfer(String subject, UUID orderId) {
    User merchant = requireMerchant(subject);
    lockTimeout.apply();
    CustomerOrder order = ownedOrderForUpdate(merchant.getId(), orderId);
    if (order.getPaymentMethod() != PaymentMethod.BANK_TRANSFER) {
      throw paymentConflict("Only a bank-transfer order can be confirmed");
    }
    if (order.getPaymentStatus() == PaymentStatus.CONFIRMED) {
      return MerchantOrderResponse.from(order);
    }
    if (order.getStatus() != OrderStatus.RESERVED) {
      throw paymentConflict("Only a reserved bank-transfer order can be confirmed");
    }

    PaymentStatus previous = order.getPaymentStatus();
    var now = clock.instant();
    order.confirmBankTransfer(merchant, now);
    notifications.notifyBoth(order, NotificationType.PAYMENT_CONFIRMED);
    events.save(
        new PaymentEvent(
            order,
            merchant,
            PaymentEventType.PAYMENT_CONFIRMED,
            previous,
            PaymentStatus.CONFIRMED,
            null,
            now));
    return MerchantOrderResponse.from(order);
  }

  @Transactional(timeout = 5)
  public MerchantOrderResponse rejectBankTransfer(
      String subject, UUID orderId, RejectPaymentRequest request) {
    User merchant = requireMerchant(subject);
    lockTimeout.apply();
    CustomerOrder order = ownedOrderForUpdate(merchant.getId(), orderId);
    if (order.getPaymentMethod() != PaymentMethod.BANK_TRANSFER
        || order.getStatus() != OrderStatus.RESERVED
        || order.getPaymentStatus() != PaymentStatus.REVIEW_REQUIRED) {
      throw paymentConflict("Only a reported bank transfer can be rejected");
    }

    var now = clock.instant();
    var reportedAt = order.getPaymentReportedAt();
    order.rejectBankTransfer();
    notifications.notifyBoth(order, NotificationType.PAYMENT_REJECTED,
        UUID.nameUUIDFromBytes((order.getPublicId() + ":" + reportedAt)
            .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    events.save(
        new PaymentEvent(
            order,
            merchant,
            PaymentEventType.PAYMENT_REJECTED,
            PaymentStatus.REVIEW_REQUIRED,
            PaymentStatus.UNPAID,
            request.reason().trim(),
            now));
    return MerchantOrderResponse.from(order);
  }

  private User requireMerchant(String subject) {
    var profile =
        merchants
            .findByUserId(OrderService.userId(subject))
            .orElseThrow(
                () ->
                    new ApiException(
                        ErrorCode.MERCHANT_PROFILE_NOT_FOUND,
                        "Merchant profile was not found"));
    User merchant = profile.getUser();
    if (merchant.getStatus() != UserStatus.VERIFIED || !merchant.hasRole(UserRole.MERCHANT)) {
      throw new ApiException(ErrorCode.ACCESS_DENIED, "Verified merchant is required");
    }
    return merchant;
  }

  private CustomerOrder ownedOrderForUpdate(Long merchantUserId, UUID orderId) {
    CustomerOrder order =
        orders
            .findForUpdate(orderId)
            .orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND, "Order was not found"));
    if (!order.getMerchant().getUser().getId().equals(merchantUserId)) {
      throw new ApiException(ErrorCode.ORDER_NOT_FOUND, "Order was not found");
    }
    return order;
  }

  private ApiException paymentConflict(String message) {
    return new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT, message);
  }
}
