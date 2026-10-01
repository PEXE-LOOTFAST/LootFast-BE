package com.lootfast.backend.order.service;

import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.common.response.PagedResponse;
import com.lootfast.backend.merchant.repository.MerchantProfileRepository;
import com.lootfast.backend.order.dto.MerchantOrderResponse;
import com.lootfast.backend.order.entity.OrderStatus;
import com.lootfast.backend.order.entity.PaymentMethod;
import com.lootfast.backend.order.entity.PaymentStatus;
import com.lootfast.backend.order.repository.OrderRepository;
import com.lootfast.backend.payment.entity.PaymentEvent;
import com.lootfast.backend.payment.entity.PaymentEventType;
import com.lootfast.backend.payment.repository.PaymentEventRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MerchantOrderService {
  private static final Duration MAX_PICKUP_RANGE = Duration.ofDays(31);
  private static final Pattern PICKUP_CODE = Pattern.compile("LF-[0-9A-Z]{6}");
  private static final String QR_PREFIX = "lootfast://pickup/";

  private final OrderRepository orders;
  private final PaymentEventRepository paymentEvents;
  private final MerchantProfileRepository merchants;
  private final MerchantPickupConfirmationRateLimiter rateLimiter;
  private final com.lootfast.backend.config.DatabaseLockTimeout lockTimeout;
  private final Clock clock;

  public MerchantOrderService(
      OrderRepository orders,
      PaymentEventRepository paymentEvents,
      MerchantProfileRepository merchants,
      MerchantPickupConfirmationRateLimiter rateLimiter,
      com.lootfast.backend.config.DatabaseLockTimeout lockTimeout,
      Clock clock) {
    this.orders = orders;
    this.paymentEvents = paymentEvents;
    this.merchants = merchants;
    this.rateLimiter = rateLimiter;
    this.lockTimeout = lockTimeout;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public PagedResponse<MerchantOrderResponse> list(
      String subject,
      Instant pickupFrom,
      Instant pickupTo,
      OrderStatus status,
      int page,
      int size) {
    Long merchantUserId = merchantUserId(subject);
    validateWindow(pickupFrom, pickupTo, page, size);
    var pageable =
        PageRequest.of(
            page,
            size,
            Sort.by("pickupStart").ascending().and(Sort.by("id").ascending()));
    var result =
        status == null
            ? orders.findMerchantPickupOrders(
                merchantUserId, pickupFrom, pickupTo, pageable)
            : orders.findMerchantPickupOrdersByStatus(
                merchantUserId, pickupFrom, pickupTo, status, pageable);
    return PagedResponse.from(result.map(MerchantOrderResponse::from));
  }

  @Transactional(timeout = 10)
  public MerchantOrderResponse confirm(String subject, String credential) {
    Long merchantUserId = merchantUserId(subject);
    rateLimiter.requireAllowed(merchantUserId);
    String pickupCode = normalizeCredential(credential);
    lockTimeout.apply();
    var order =
        orders
            .findMerchantOrderByPickupCodeForUpdate(merchantUserId, pickupCode)
            .orElseThrow(
                () ->
                    new ApiException(
                        ErrorCode.ORDER_NOT_FOUND, "Pickup order was not found"));
    if (order.getStatus() != OrderStatus.RESERVED) {
      throw new ApiException(
          ErrorCode.ORDER_NOT_COMPLETABLE, "Order has already been finalized");
    }
    Instant now = clock.instant();
    if (order.getPaymentMethod() == PaymentMethod.CASH) {
      order.collectCash(order.getMerchant().getUser(), now);
      paymentEvents.save(
          new PaymentEvent(
              order,
              order.getMerchant().getUser(),
              PaymentEventType.CASH_COLLECTED,
              PaymentStatus.UNPAID,
              PaymentStatus.CONFIRMED,
              null,
              now));
    } else if (order.getPaymentStatus() != PaymentStatus.CONFIRMED) {
      throw new ApiException(
          ErrorCode.PAYMENT_STATE_CONFLICT,
          "Bank transfer must be confirmed before pickup completion");
    }
    order.complete(now);
    return MerchantOrderResponse.from(order);
  }

  private Long merchantUserId(String subject) {
    Long userId = OrderService.userId(subject);
    merchants
        .findByUserId(userId)
        .orElseThrow(
            () ->
                new ApiException(
                    ErrorCode.MERCHANT_PROFILE_NOT_FOUND, "Merchant profile was not found"));
    return userId;
  }

  private void validateWindow(Instant pickupFrom, Instant pickupTo, int page, int size) {
    if (pickupFrom == null
        || pickupTo == null
        || !pickupFrom.isBefore(pickupTo)
        || Duration.between(pickupFrom, pickupTo).compareTo(MAX_PICKUP_RANGE) > 0
        || page < 0
        || size < 1
        || size > 100) {
      throw new ApiException(
          ErrorCode.INVALID_PICKUP_WINDOW,
          "Pickup range must be positive, at most 31 days, with page >= 0 and size 1 to 100");
    }
  }

  private String normalizeCredential(String credential) {
    String normalized = credential == null ? "" : credential.trim();
    if (normalized.regionMatches(true, 0, QR_PREFIX, 0, QR_PREFIX.length())) {
      normalized = normalized.substring(QR_PREFIX.length());
    }
    normalized = normalized.toUpperCase(Locale.ROOT);
    if (!PICKUP_CODE.matcher(normalized).matches()) {
      throw new ApiException(
          ErrorCode.INVALID_PICKUP_CREDENTIAL, "Pickup credential is invalid");
    }
    return normalized;
  }
}
