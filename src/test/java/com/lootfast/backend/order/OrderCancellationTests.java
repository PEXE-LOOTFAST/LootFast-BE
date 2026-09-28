package com.lootfast.backend.order;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.deal.entity.Deal;
import com.lootfast.backend.deal.entity.DealStatus;
import com.lootfast.backend.deal.repository.DealRepository;
import com.lootfast.backend.merchant.entity.MerchantProfile;
import com.lootfast.backend.merchant.repository.MerchantProfileRepository;
import com.lootfast.backend.order.dto.CreateOrderRequest;
import com.lootfast.backend.order.dto.OrderResponse;
import com.lootfast.backend.order.entity.OrderStatus;
import com.lootfast.backend.order.entity.PaymentMethod;
import com.lootfast.backend.order.repository.OrderRepository;
import com.lootfast.backend.order.service.OrderService;
import com.lootfast.backend.user.entity.User;
import com.lootfast.backend.user.entity.UserRole;
import com.lootfast.backend.user.entity.UserStatus;
import com.lootfast.backend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class OrderCancellationTests {
  private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");

  @Autowired UserRepository users;
  @Autowired MerchantProfileRepository merchants;
  @Autowired DealRepository deals;
  @Autowired OrderRepository orders;
  @Autowired OrderService service;

  @MockitoBean Clock clock;

  User buyer;
  MerchantProfile merchant;

  @BeforeEach
  void setup() {
    when(clock.instant()).thenReturn(NOW);
    orders.deleteAll();
    deals.deleteAll();
    merchants.deleteAll();
    users.deleteAll();

    buyer =
        users.save(
            new User(
                "Buyer", "One", UUID.randomUUID() + "@test.local",
                null, null, UserRole.BUYER, UserStatus.VERIFIED));
    User merchantUser =
        users.save(
            new User(
                "Merchant", "One", UUID.randomUUID() + "@test.local",
                null, null, UserRole.MERCHANT, UserStatus.VERIFIED));
    merchant = new MerchantProfile(merchantUser);
    merchant.updateStoreInformation("Test store", "description", "MEAL", null, null);
    merchant.updateLocation("1 Test", "Ward", "District", "City", BigDecimal.TEN, BigDecimal.TEN);
    merchants.save(merchant);
  }

  @Test
  void cancelRestoresStockAndMarksCancelled() {
    Deal deal = activeDeal(5);
    UUID orderId = reserve(deal, 2).orderId();
    assertEquals(3, remaining(deal));

    OrderResponse cancelled = service.cancel(buyer.getId().toString(), orderId);

    assertEquals(OrderStatus.CANCELLED, cancelled.orderStatus());
    assertNotNull(cancelled.finalizedAt());
    assertEquals(5, remaining(deal));
  }

  @Test
  void cancelRejectedInsideThirtyMinuteWindowWithoutTouchingStock() {
    Deal deal = activeDeal(5);
    UUID orderId = reserve(deal, 2).orderId();

    when(clock.instant()).thenReturn(NOW.plusSeconds(3000)); // pickupStart is NOW+3600
    ApiException e =
        assertThrows(ApiException.class, () -> service.cancel(buyer.getId().toString(), orderId));

    assertEquals(ErrorCode.ORDER_CANCEL_WINDOW_CLOSED, e.getErrorCode());
    assertEquals(3, remaining(deal));
  }

  @Test
  void cancelRejectedWhenAlreadyFinalized() {
    Deal deal = activeDeal(5);
    UUID orderId = reserve(deal, 2).orderId();
    service.cancel(buyer.getId().toString(), orderId);

    ApiException e =
        assertThrows(ApiException.class, () -> service.cancel(buyer.getId().toString(), orderId));
    assertEquals(ErrorCode.ORDER_NOT_CANCELLABLE, e.getErrorCode());
    assertEquals(5, remaining(deal));
  }

  @Test
  void cancelByAnotherBuyerReturnsNotFound() {
    Deal deal = activeDeal(5);
    UUID orderId = reserve(deal, 2).orderId();
    User intruder =
        users.save(
            new User(
                "Buyer", "Two", UUID.randomUUID() + "@test.local",
                null, null, UserRole.BUYER, UserStatus.VERIFIED));

    ApiException e =
        assertThrows(
            ApiException.class, () -> service.cancel(intruder.getId().toString(), orderId));
    assertEquals(ErrorCode.ORDER_NOT_FOUND, e.getErrorCode());
    assertEquals(3, remaining(deal));
  }

  @Test
  void noShowSweepFinalizesAndRestoresStockExactlyOnce() {
    Deal deal = activeDeal(5); // pickupEnd is NOW+7200
    UUID orderId = reserve(deal, 2).orderId();

    when(clock.instant()).thenReturn(NOW.plusSeconds(9001)); // past pickupEnd + 30 minutes
    assertTrue(service.findNoShowCandidates().contains(orderId));

    service.markNoShow(orderId);
    assertEquals(5, remaining(deal));
    assertEquals(
        OrderStatus.NO_SHOW,
        orders.findByPublicIdAndBuyerId(orderId, buyer.getId()).orElseThrow().getStatus());

    // Second sweep is a no-op: stock is not restored twice.
    service.markNoShow(orderId);
    assertEquals(5, remaining(deal));
  }

  @Test
  void noShowSkippedBeforePickupEndPlusThirtyMinutes() {
    Deal deal = activeDeal(5);
    UUID orderId = reserve(deal, 2).orderId();

    when(clock.instant()).thenReturn(NOW.plusSeconds(7200)); // exactly pickupEnd, before +30m
    assertFalse(service.findNoShowCandidates().contains(orderId));

    service.markNoShow(orderId);
    assertEquals(3, remaining(deal));
    assertEquals(
        OrderStatus.RESERVED,
        orders.findByPublicIdAndBuyerId(orderId, buyer.getId()).orElseThrow().getStatus());
  }

  private OrderResponse reserve(Deal deal, int quantity) {
    return service.reserve(
        buyer.getId().toString(),
        UUID.randomUUID(),
        new CreateOrderRequest(deal.getPublicId(), quantity, PaymentMethod.CASH));
  }

  private int remaining(Deal deal) {
    return deals.findByPublicId(deal.getPublicId()).orElseThrow().getRemainingQuantity();
  }

  private Deal activeDeal(int stock) {
    return deals.saveAndFlush(
        new Deal(
            merchant,
            "Meal",
            "MEAL",
            BigDecimal.valueOf(100_000),
            BigDecimal.valueOf(50_000),
            stock,
            "https://example.test/image.jpg",
            10,
            NOW.plusSeconds(3600),
            NOW.plusSeconds(7200),
            NOW.minusSeconds(60),
            NOW.plusSeconds(7200),
            DealStatus.ACTIVE));
  }
}
