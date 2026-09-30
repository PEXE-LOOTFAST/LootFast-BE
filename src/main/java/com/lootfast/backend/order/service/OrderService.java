package com.lootfast.backend.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lootfast.backend.common.exception.*;
import com.lootfast.backend.common.response.PagedResponse;
import com.lootfast.backend.deal.entity.*;
import com.lootfast.backend.deal.repository.DealRepository;
import com.lootfast.backend.merchant.repository.*;
import com.lootfast.backend.order.dto.*;
import com.lootfast.backend.order.entity.*;
import com.lootfast.backend.order.repository.OrderRepository;
import com.lootfast.backend.user.entity.*;
import com.lootfast.backend.user.repository.UserRepository;
import java.time.*;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
  // BR-ORD-09: 3 no-show trong 30 ngày thì 14 ngày kế chỉ được đặt đơn trả trước (chặn tiền mặt).
  private static final int NO_SHOW_THRESHOLD = 3;
  private static final long NO_SHOW_WINDOW_SECONDS = 30L * 24 * 3600;
  private static final long NO_SHOW_PENALTY_SECONDS = 14L * 24 * 3600;

  private final com.lootfast.backend.config.DatabaseLockTimeout lockTimeout;
  private final UserRepository users;
  private final DealRepository deals;
  private final MerchantProfileRepository merchants;
  private final MerchantBankAccountRepository banks;
  private final OrderRepository orders;
  private final ObjectMapper json;
  private final Clock clock;

  public OrderService(
      UserRepository users,
      DealRepository deals,
      MerchantProfileRepository merchants,
      MerchantBankAccountRepository banks,
      OrderRepository orders,
      ObjectMapper json,
      Clock clock,
      com.lootfast.backend.config.DatabaseLockTimeout lockTimeout) {
    this.lockTimeout = lockTimeout;
    this.users = users;
    this.deals = deals;
    this.merchants = merchants;
    this.banks = banks;
    this.orders = orders;
    this.json = json;
    this.clock = clock;
  }

  public static Long userId(String subject) {
    try {
      return Long.valueOf(subject);
    } catch (NumberFormatException | NullPointerException e) {
      throw error(ErrorCode.USER_NOT_FOUND, "User is unavailable");
    }
  }

  public static ApiException error(ErrorCode code, String message) {
    return new ApiException(code, message);
  }

  @Transactional(timeout = 10)
  public OrderResponse reserve(String subject, UUID key, CreateOrderRequest request) {
    if (key == null
        || request.dealId() == null
        || request.paymentMethod() == null
        || request.quantity() < 1
        || request.quantity() > 5)
      throw error(
          ErrorCode.VALIDATION_FAILED, "Quantity must be 1 to 5 and all fields are required");
    lockTimeout.apply();
    User buyer =
        users
            .findByIdForUpdate(userId(subject))
            .orElseThrow(() -> error(ErrorCode.USER_NOT_FOUND, "User is unavailable"));
    requireBuyer(buyer);
    String fingerprint =
        request.dealId() + ":" + request.quantity() + ":" + request.paymentMethod();
    var existing = orders.findByBuyerIdAndReservationKey(buyer.getId(), key);
    if (existing.isPresent()) {
      CustomerOrder original = existing.get();
      if (!original.getRequestFingerprint().equals(fingerprint))
        throw error(ErrorCode.IDEMPOTENCY_CONFLICT, "Key was used for another request");
      if (!clock.instant().isBefore(original.getReplayUntil()))
        throw error(ErrorCode.IDEMPOTENCY_KEY_EXPIRED, "Use a new key after 24 hours");
      try {
        return json.readValue(original.getCreationResponse(), OrderResponse.class);
      } catch (JsonProcessingException e) {
        throw new IllegalStateException("Stored reservation response is invalid");
      }
    }
    Long merchantUserId =
        deals
            .findMerchantUserId(request.dealId())
            .orElseThrow(() -> error(ErrorCode.DEAL_NOT_FOUND, "Deal was not found"));
    // Merchant lock also serializes reservations with bank-account changes.
    merchants
        .findByUserIdForUpdate(merchantUserId)
        .orElseThrow(() -> error(ErrorCode.MERCHANT_PROFILE_NOT_FOUND, "Store is unavailable"));
    Deal deal =
        deals
            .findReservationForUpdate(request.dealId())
            .orElseThrow(() -> error(ErrorCode.DEAL_NOT_FOUND, "Deal was not found"));
    Instant now = clock.instant(); // Read after waiting for all locks.
    if (deal.getStatus() != DealStatus.ACTIVE
        || now.isBefore(deal.getAvailableFrom())
        || !now.isBefore(deal.getAvailableUntil()))
      throw error(ErrorCode.DEAL_NOT_RESERVABLE, "Deal is not on sale");
    if (!now.isBefore(deal.getPickupStart().minusSeconds(900)))
      throw error(
          ErrorCode.ORDER_CUTOFF_REACHED, "Reservations close 15 minutes before pickup starts");
    var day = deal.getAvailableFrom().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate();
    Instant dayStart = day.atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
    Instant dayEnd = day.plusDays(1).atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
    if (deals.countScheduleConflicts(
            deal.getMerchant().getId(),
            deal.getPublicId(),
            java.util.Set.of(DealStatus.ACTIVE, DealStatus.HIDDEN, DealStatus.SOLD_OUT),
            dayStart,
            dayEnd,
            deal.getAvailableFrom(),
            deal.getAvailableUntil(),
            deal.getPickupStart(),
            deal.getPickupEnd())
        > 0)
      throw error(
          ErrorCode.STORE_SCHEDULE_CONFLICT,
          "Store must use one sale and one pickup window per day");
    if (orders.countByBuyerIdAndStatus(buyer.getId(), OrderStatus.RESERVED) >= 3)
      throw error(ErrorCode.RESERVED_ORDER_LIMIT, "At most 3 reserved orders are allowed");
    if (orders.reservedQuantity(buyer.getId(), request.dealId()) + request.quantity() > 5)
      throw error(
          ErrorCode.RESERVED_QUANTITY_LIMIT, "At most 5 uncollected portions per deal are allowed");
    if (request.paymentMethod() == PaymentMethod.CASH && isNoShowRestricted(buyer.getId(), now))
      throw error(
          ErrorCode.NO_SHOW_RESTRICTED,
          "Too many recent no-shows; pay by bank transfer to reserve for now");
    if (deal.getRemainingQuantity() < request.quantity())
      throw error(ErrorCode.INSUFFICIENT_STOCK, "Not enough portions remain");
    CustomerOrder order =
        new CustomerOrder(
            buyer, deal, request.quantity(), request.paymentMethod(), key, fingerprint, now);
    var bank = banks.findByMerchantUserId(deal.getMerchant().getUser().getId());
    bank.ifPresent(
        b ->
            order.snapshotBank(
                b.getBankBin(), b.getBankName(), b.getAccountNumber(), b.getAccountHolderName()));
    if (request.paymentMethod() == PaymentMethod.BANK_TRANSFER && bank.isEmpty())
      throw error(ErrorCode.BANK_ACCOUNT_NOT_FOUND, "Store has not configured bank transfers");
    deal.reserve(request.quantity());
    OrderResponse response = OrderResponse.from(order);
    try {
      order.setCreationResponse(json.writeValueAsString(response));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Cannot serialize reservation response");
    }
    orders.saveAndFlush(order);
    return response;
  }

  @Transactional(timeout = 10)
  public OrderResponse cancel(String subject, UUID orderId) {
    lockTimeout.apply();
    CustomerOrder order =
        orders
            .findForUpdate(orderId)
            .orElseThrow(() -> error(ErrorCode.ORDER_NOT_FOUND, "Order was not found"));
    if (!order.getBuyer().getId().equals(userId(subject)))
      throw error(ErrorCode.ORDER_NOT_FOUND, "Order was not found");
    if (order.getStatus() != OrderStatus.RESERVED)
      throw error(ErrorCode.ORDER_NOT_CANCELLABLE, "Order can no longer be cancelled");
    Instant now = clock.instant();
    if (!now.isBefore(order.getPickupStart().minusSeconds(1800)))
      throw error(
          ErrorCode.ORDER_CANCEL_WINDOW_CLOSED, "Cancel at least 30 minutes before pickup starts");
    Deal deal =
        deals
            .findReservationForUpdate(order.getDeal().getPublicId())
            .orElseThrow(() -> error(ErrorCode.DEAL_NOT_FOUND, "Deal was not found"));
    deal.restore(order.getQuantity(), now);
    order.cancel(now);
    return OrderResponse.from(order);
  }

  @Transactional(readOnly = true)
  public List<UUID> findNoShowCandidates() {
    return orders.findNoShowCandidates(clock.instant().minusSeconds(1800));
  }

  /** Called per order by the sweeper; skips silently if the order changed since it was queued. */
  @Transactional(timeout = 10)
  public void markNoShow(UUID orderId) {
    lockTimeout.apply();
    CustomerOrder order = orders.findForUpdate(orderId).orElse(null);
    if (order == null || order.getStatus() != OrderStatus.RESERVED) return;
    Instant now = clock.instant();
    if (now.isBefore(order.getPickupEnd().plusSeconds(1800))) return;
    Deal deal =
        deals
            .findReservationForUpdate(order.getDeal().getPublicId())
            .orElseThrow(() -> error(ErrorCode.DEAL_NOT_FOUND, "Deal was not found"));
    deal.restore(order.getQuantity(), now);
    order.markNoShow(now);
  }

  @Transactional(readOnly = true)
  public PagedResponse<OrderResponse> list(String subject, OrderStatus status, int page, int size) {
    Long buyerId = userId(subject);
    var pageable =
        PageRequest.of(page, size, Sort.by("createdAt").descending().and(Sort.by("id").descending()));
    var orderPage =
        status == null
            ? orders.findByBuyerId(buyerId, pageable)
            : orders.findByBuyerIdAndStatus(buyerId, status, pageable);
    return PagedResponse.from(orderPage.map(OrderResponse::from));
  }

  @Transactional(readOnly = true)
  public OrderResponse get(String subject, UUID orderId) {
    return OrderResponse.from(
        orders
            .findByPublicIdAndBuyerId(orderId, userId(subject))
            .orElseThrow(() -> error(ErrorCode.ORDER_NOT_FOUND, "Order was not found")));
  }

  /**
   * True nếu người mua đang trong thời gian bị hạn chế no-show: có ít nhất {@code NO_SHOW_THRESHOLD}
   * đơn NO_SHOW trong 30 ngày gần nhất và chưa qua 14 ngày kể từ lần no-show mới nhất.
   */
  private boolean isNoShowRestricted(Long buyerId, Instant now) {
    var noShowTimes = orders.recentNoShowTimes(buyerId, now.minusSeconds(NO_SHOW_WINDOW_SECONDS));
    return noShowTimes.size() >= NO_SHOW_THRESHOLD
        && now.isBefore(noShowTimes.get(0).plusSeconds(NO_SHOW_PENALTY_SECONDS));
  }

  private void requireBuyer(User user) {
    if (!user.hasRole(UserRole.BUYER))
      throw error(ErrorCode.ACCESS_DENIED, "Buyer role is required");
    if (user.getStatus() != UserStatus.VERIFIED)
      throw error(ErrorCode.ACCOUNT_NOT_VERIFIED, "Account must be verified");
  }
}
