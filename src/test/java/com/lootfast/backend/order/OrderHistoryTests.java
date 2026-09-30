package com.lootfast.backend.order;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import com.lootfast.backend.common.response.PagedResponse;
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
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class OrderHistoryTests {
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
    buyer = buyer("Buyer", "One");
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
  void historyReturnsSavingsComputedFromSnapshottedOriginalPrice() {
    Deal deal = activeDeal(5); // original 100k, discounted 50k
    reserve(deal, 2);

    PagedResponse<OrderResponse> history = service.list(buyer.getId().toString(), null, 0, 20);

    assertEquals(1, history.totalElements());
    OrderResponse order = history.items().get(0);
    assertEquals(0, BigDecimal.valueOf(100_000).compareTo(order.originalUnitPrice()));
    assertEquals(0, BigDecimal.valueOf(50_000).compareTo(order.unitPrice()));
    // savings = (100k - 50k) * 2
    assertEquals(0, BigDecimal.valueOf(100_000).compareTo(order.savings()));
  }

  @Test
  void historyShowsOnlyOwnOrders() {
    Deal deal = activeDeal(5);
    reserve(deal, 1);
    User intruder = buyer("Buyer", "Two");
    service.reserve(
        intruder.getId().toString(),
        UUID.randomUUID(),
        new CreateOrderRequest(deal.getPublicId(), 1, PaymentMethod.CASH));

    PagedResponse<OrderResponse> mine = service.list(buyer.getId().toString(), null, 0, 20);
    assertEquals(1, mine.totalElements());
  }

  @Test
  void historyFiltersByStatus() {
    Deal deal = activeDeal(5);
    UUID toCancel = reserve(deal, 1).orderId();
    reserve(deal, 1); // stays RESERVED
    service.cancel(buyer.getId().toString(), toCancel);

    assertEquals(
        1, service.list(buyer.getId().toString(), OrderStatus.CANCELLED, 0, 20).totalElements());
    assertEquals(
        1, service.list(buyer.getId().toString(), OrderStatus.RESERVED, 0, 20).totalElements());
  }

  @Test
  void historyPaginatesNewestFirst() {
    Deal deal = activeDeal(5);
    reserve(deal, 1);
    reserve(deal, 1);
    reserve(deal, 1);

    PagedResponse<OrderResponse> firstPage = service.list(buyer.getId().toString(), null, 0, 2);
    assertEquals(3, firstPage.totalElements());
    assertEquals(2, firstPage.totalPages());
    assertEquals(2, firstPage.items().size());

    PagedResponse<OrderResponse> secondPage = service.list(buyer.getId().toString(), null, 1, 2);
    assertEquals(1, secondPage.items().size());
  }

  private OrderResponse reserve(Deal deal, int quantity) {
    return service.reserve(
        buyer.getId().toString(),
        UUID.randomUUID(),
        new CreateOrderRequest(deal.getPublicId(), quantity, PaymentMethod.CASH));
  }

  private User buyer(String first, String last) {
    return users.save(
        new User(
            first, last, UUID.randomUUID() + "@test.local",
            null, null, UserRole.BUYER, UserStatus.VERIFIED));
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
