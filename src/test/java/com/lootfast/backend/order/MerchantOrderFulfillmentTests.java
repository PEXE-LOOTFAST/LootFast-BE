package com.lootfast.backend.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import com.lootfast.backend.order.service.MerchantOrderService;
import com.lootfast.backend.order.service.MerchantPickupConfirmationRateLimiter;
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
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class MerchantOrderFulfillmentTests {
  private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");

  @Autowired UserRepository users;
  @Autowired MerchantProfileRepository merchants;
  @Autowired DealRepository deals;
  @Autowired OrderRepository orders;
  @Autowired OrderService orderService;
  @Autowired MerchantOrderService merchantOrderService;

  @MockitoBean Clock clock;
  @MockitoBean MerchantPickupConfirmationRateLimiter rateLimiter;

  User buyer;
  User merchantUser;
  User otherMerchantUser;
  MerchantProfile merchant;

  @BeforeEach
  void setup() {
    when(clock.instant()).thenReturn(NOW);
    buyer = user("Buyer", UserRole.BUYER);
    merchantUser = user("Merchant", UserRole.MERCHANT);
    otherMerchantUser = user("Other", UserRole.MERCHANT);
    merchant = merchant(merchantUser, "Main store");
    merchant(otherMerchantUser, "Other store");
  }

  @Test
  void reservationExposesDistinctPickupCodeAndQrPayload() {
    Deal deal = activeDeal(merchant, "Meal", 4);

    OrderResponse first = reserve(deal, 1);
    OrderResponse second = reserve(deal, 1);

    assertTrue(first.pickupCode().matches("LF-[23456789A-HJ-NP-Z]{6}"));
    assertEquals("lootfast://pickup/" + first.pickupCode(), first.pickupQrPayload());
    assertNotEquals(first.pickupCode(), first.paymentReference());
    assertNotEquals(first.pickupCode(), second.pickupCode());
  }

  @Test
  void merchantListsOnlyOwnOrdersInsidePickupWindow() {
    Deal ownDeal = activeDeal(merchant, "Own meal", 3);
    MerchantProfile other = merchants.findByUserId(otherMerchantUser.getId()).orElseThrow();
    Deal otherDeal = activeDeal(other, "Other meal", 3);
    OrderResponse ownOrder = reserve(ownDeal, 2);
    reserve(otherDeal, 1);

    var page =
        merchantOrderService.list(
            merchantUser.getId().toString(),
            NOW,
            NOW.plusSeconds(3 * 3600),
            OrderStatus.RESERVED,
            0,
            20);

    assertEquals(1, page.totalElements());
    assertEquals(ownOrder.orderId(), page.items().get(0).orderId());
    assertEquals(2, page.items().get(0).quantity());
  }

  @Test
  void merchantCompletesByQrExactlyOnceAndAnotherStoreCannotUseTheCode() {
    OrderResponse order = reserve(activeDeal(merchant, "Pickup meal", 3), 1);

    ApiException wrongStore =
        assertThrows(
            ApiException.class,
            () ->
                merchantOrderService.confirm(
                    otherMerchantUser.getId().toString(), order.pickupCode()));
    assertEquals(ErrorCode.ORDER_NOT_FOUND, wrongStore.getErrorCode());

    var completed =
        merchantOrderService.confirm(
            merchantUser.getId().toString(), order.pickupQrPayload());
    assertEquals(OrderStatus.COMPLETED, completed.orderStatus());
    assertEquals(NOW, completed.finalizedAt());

    ApiException replay =
        assertThrows(
            ApiException.class,
            () ->
                merchantOrderService.confirm(
                    merchantUser.getId().toString(), order.pickupCode()));
    assertEquals(ErrorCode.ORDER_NOT_COMPLETABLE, replay.getErrorCode());
    assertEquals(
        OrderStatus.COMPLETED,
        orders.findByPublicIdAndBuyerId(order.orderId(), buyer.getId()).orElseThrow().getStatus());
  }

  @Test
  void rejectsInvalidWindowsAndCredentials() {
    ApiException invalidWindow =
        assertThrows(
            ApiException.class,
            () ->
                merchantOrderService.list(
                    merchantUser.getId().toString(), NOW, NOW, null, 0, 20));
    assertEquals(ErrorCode.INVALID_PICKUP_WINDOW, invalidWindow.getErrorCode());

    ApiException invalidCredential =
        assertThrows(
            ApiException.class,
            () -> merchantOrderService.confirm(merchantUser.getId().toString(), "not-a-code"));
    assertEquals(ErrorCode.INVALID_PICKUP_CREDENTIAL, invalidCredential.getErrorCode());
  }

  private OrderResponse reserve(Deal deal, int quantity) {
    return orderService.reserve(
        buyer.getId().toString(),
        UUID.randomUUID(),
        new CreateOrderRequest(deal.getPublicId(), quantity, PaymentMethod.CASH));
  }

  private User user(String name, UserRole role) {
    return users.save(
        new User(
            name,
            "Test",
            UUID.randomUUID() + "@test.local",
            null,
            null,
            role,
            UserStatus.VERIFIED));
  }

  private MerchantProfile merchant(User user, String storeName) {
    MerchantProfile profile = new MerchantProfile(user);
    profile.updateStoreInformation(storeName, "description", "MEAL", null, null);
    profile.updateLocation("1 Test", "Ward", "District", "City", BigDecimal.TEN, BigDecimal.TEN);
    return merchants.save(profile);
  }

  private Deal activeDeal(MerchantProfile owner, String name, int stock) {
    return deals.saveAndFlush(
        new Deal(
            owner,
            name,
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
