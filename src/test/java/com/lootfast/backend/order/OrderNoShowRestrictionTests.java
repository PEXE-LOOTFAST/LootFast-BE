package com.lootfast.backend.order;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.deal.entity.Deal;
import com.lootfast.backend.deal.entity.DealStatus;
import com.lootfast.backend.deal.repository.DealRepository;
import com.lootfast.backend.merchant.entity.MerchantBankAccount;
import com.lootfast.backend.merchant.entity.MerchantProfile;
import com.lootfast.backend.merchant.repository.MerchantBankAccountRepository;
import com.lootfast.backend.merchant.repository.MerchantProfileRepository;
import com.lootfast.backend.order.dto.CreateOrderRequest;
import com.lootfast.backend.order.dto.OrderResponse;
import com.lootfast.backend.order.entity.CustomerOrder;
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
class OrderNoShowRestrictionTests {
  private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
  private static final long DAY = 24 * 3600;

  @Autowired UserRepository users;
  @Autowired MerchantProfileRepository merchants;
  @Autowired MerchantBankAccountRepository banks;
  @Autowired DealRepository deals;
  @Autowired OrderRepository orders;
  @Autowired OrderService service;

  @MockitoBean Clock clock;

  User buyer;
  MerchantProfile merchant;

  @BeforeEach
  void setup() {
    when(clock.instant()).thenReturn(NOW);
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
  void cashBlockedAfterThreeRecentNoShows() {
    Deal deal = activeDeal();
    createNoShow(deal, NOW.minusSeconds(DAY));
    createNoShow(deal, NOW.minusSeconds(2 * DAY));
    createNoShow(deal, NOW.minusSeconds(3 * DAY));

    ApiException e = assertThrows(ApiException.class, () -> reserveCash(deal));
    assertEquals(ErrorCode.NO_SHOW_RESTRICTED, e.getErrorCode());
  }

  @Test
  void twoRecentNoShowsDoNotBlockCash() {
    Deal deal = activeDeal();
    createNoShow(deal, NOW.minusSeconds(DAY));
    createNoShow(deal, NOW.minusSeconds(2 * DAY));

    OrderResponse order = reserveCash(deal);
    assertEquals(OrderStatus.RESERVED, order.orderStatus());
  }

  @Test
  void bankTransferAllowedDespiteNoShows() {
    Deal deal = activeDeal();
    banks.save(
        new MerchantBankAccount(merchant, "970415", "VietinBank", "113366668888", "NGUYEN VAN A"));
    createNoShow(deal, NOW.minusSeconds(DAY));
    createNoShow(deal, NOW.minusSeconds(2 * DAY));
    createNoShow(deal, NOW.minusSeconds(3 * DAY));

    OrderResponse order =
        service.reserve(
            buyer.getId().toString(),
            UUID.randomUUID(),
            new CreateOrderRequest(deal.getPublicId(), 1, PaymentMethod.BANK_TRANSFER));
    assertEquals(OrderStatus.RESERVED, order.orderStatus());
  }

  @Test
  void penaltyLiftsFourteenDaysAfterLatestNoShow() {
    Deal deal = activeDeal();
    // Still inside the 30-day window, but the latest no-show is older than 14 days.
    createNoShow(deal, NOW.minusSeconds(15 * DAY));
    createNoShow(deal, NOW.minusSeconds(16 * DAY));
    createNoShow(deal, NOW.minusSeconds(17 * DAY));

    OrderResponse order = reserveCash(deal);
    assertEquals(OrderStatus.RESERVED, order.orderStatus());
  }

  @Test
  void noShowsOlderThanThirtyDaysAreIgnored() {
    Deal deal = activeDeal();
    createNoShow(deal, NOW.minusSeconds(31 * DAY));
    createNoShow(deal, NOW.minusSeconds(32 * DAY));
    createNoShow(deal, NOW.minusSeconds(33 * DAY));

    OrderResponse order = reserveCash(deal);
    assertEquals(OrderStatus.RESERVED, order.orderStatus());
  }

  private OrderResponse reserveCash(Deal deal) {
    return service.reserve(
        buyer.getId().toString(),
        UUID.randomUUID(),
        new CreateOrderRequest(deal.getPublicId(), 1, PaymentMethod.CASH));
  }

  private void createNoShow(Deal deal, Instant finalizedAt) {
    CustomerOrder order =
        new CustomerOrder(buyer, deal, 1, PaymentMethod.CASH, UUID.randomUUID(), "fp", NOW);
    order.markNoShow(finalizedAt);
    orders.saveAndFlush(order);
  }

  private Deal activeDeal() {
    return deals.saveAndFlush(
        new Deal(
            merchant,
            "Meal",
            "MEAL",
            BigDecimal.valueOf(100_000),
            BigDecimal.valueOf(50_000),
            5,
            "https://example.test/image.jpg",
            10,
            NOW.plusSeconds(3600),
            NOW.plusSeconds(7200),
            NOW.minusSeconds(60),
            NOW.plusSeconds(7200),
            DealStatus.ACTIVE));
  }
}
