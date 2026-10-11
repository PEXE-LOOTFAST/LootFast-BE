package com.lootfast.backend.order;

import static org.junit.jupiter.api.Assertions.*;

import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.deal.entity.*;
import com.lootfast.backend.deal.repository.DealRepository;
import com.lootfast.backend.merchant.entity.*;
import com.lootfast.backend.merchant.repository.*;
import com.lootfast.backend.merchant.service.MerchantBankAccountService;
import com.lootfast.backend.order.dto.*;
import com.lootfast.backend.order.entity.*;
import com.lootfast.backend.order.repository.OrderRepository;
import com.lootfast.backend.order.service.OrderService;
import com.lootfast.backend.payment.service.PaymentTransactionService;
import com.lootfast.backend.user.entity.*;
import com.lootfast.backend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class OrderServiceIntegrationTests {
  private static final Instant NOW = Instant.now();
  @Autowired UserRepository users;
  @Autowired MerchantProfileRepository merchants;
  @Autowired MerchantBankAccountRepository banks;
  @Autowired DealRepository deals;
  @Autowired OrderRepository orders;
  @Autowired OrderService service;
  @Autowired PaymentTransactionService payments;
  @Autowired MerchantBankAccountService bankService;

  User buyer;
  User merchantUser;
  MerchantProfile merchant;

  @BeforeEach
  void setup() {
    buyer =
        users.save(
            new User(
                "Buyer",
                "One",
                UUID.randomUUID() + "@test.local",
                null,
                null,
                UserRole.BUYER,
                UserStatus.VERIFIED));
    merchantUser =
        users.save(
            new User(
                "Merchant",
                "One",
                UUID.randomUUID() + "@test.local",
                null,
                null,
                UserRole.MERCHANT,
                UserStatus.VERIFIED));
    merchant = new MerchantProfile(merchantUser);
    merchant.updateStoreInformation("Test store", "description", "MEAL", null, null);
    merchant.updateLocation("1 Test", "Ward", "District", "City", BigDecimal.TEN, BigDecimal.TEN);
    merchants.save(merchant);
  }

  @Test
  void reservationIsAtomicIdempotentAndSnapshotsItsPriceAndWindow() {
    Deal deal =
        deal(
            "Meal",
            10,
            NOW.minusSeconds(60),
            NOW.plusSeconds(7200),
            NOW.plusSeconds(3600),
            NOW.plusSeconds(7200));
    UUID key = UUID.randomUUID();
    var request = new CreateOrderRequest(deal.getPublicId(), 2, PaymentMethod.CASH);
    OrderResponse first = service.reserve(buyer.getId().toString(), key, request);
    OrderResponse replay = service.reserve(buyer.getId().toString(), key, request);

    assertEquals(first, replay);
    assertEquals(BigDecimal.valueOf(100_000), first.totalAmount());
    assertEquals(1, orders.count());
    assertEquals(8, deals.findByPublicId(deal.getPublicId()).orElseThrow().getRemainingQuantity());

    ApiException conflict =
        assertThrows(
            ApiException.class,
            () ->
                service.reserve(
                    buyer.getId().toString(),
                    key,
                    new CreateOrderRequest(deal.getPublicId(), 1, PaymentMethod.CASH)));
    assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT, conflict.getErrorCode());
    assertEquals(8, deals.findByPublicId(deal.getPublicId()).orElseThrow().getRemainingQuantity());
  }

  @Test
  void buyerChosenPickupSlotIsStoredWhenInsideWindowAndRejectedOutside() {
    Deal deal =
        deal(
            "Meal",
            10,
            NOW.minusSeconds(60),
            NOW.plusSeconds(7200),
            NOW.plusSeconds(3600),
            NOW.plusSeconds(7200));
    Instant from = NOW.plusSeconds(4200); // khoảng con trong khung [+3600, +7200]
    Instant to = NOW.plusSeconds(6000);
    OrderResponse response =
        service.reserve(
            buyer.getId().toString(),
            UUID.randomUUID(),
            new CreateOrderRequest(deal.getPublicId(), 1, PaymentMethod.CASH, from, to));
    assertEquals(from, response.pickupFrom());
    assertEquals(to, response.pickupTo());

    // pickupTo vượt khung pickup của deal -> bị từ chối.
    ApiException outside =
        assertThrows(
            ApiException.class,
            () ->
                service.reserve(
                    buyer.getId().toString(),
                    UUID.randomUUID(),
                    new CreateOrderRequest(
                        deal.getPublicId(), 1, PaymentMethod.CASH, from, NOW.plusSeconds(8000))));
    assertEquals(ErrorCode.ORDER_PICKUP_TIME_INVALID, outside.getErrorCode());
  }

  @Test
  void limitsFiveUncollectedPortionsAcrossOrdersAndThreeReservedOrdersOverall() {
    Deal deal =
        deal(
            "Shared deal",
            10,
            NOW.minusSeconds(60),
            NOW.plusSeconds(7200),
            NOW.plusSeconds(3600),
            NOW.plusSeconds(7200));
    reserve(deal, 2);
    reserve(deal, 3);
    ApiException portions = assertThrows(ApiException.class, () -> reserve(deal, 1));
    assertEquals(ErrorCode.RESERVED_QUANTITY_LIMIT, portions.getErrorCode());

    Deal second =
        deal(
            "Second",
            5,
            deal.getAvailableFrom(),
            deal.getAvailableUntil(),
            deal.getPickupStart(),
            deal.getPickupEnd());
    reserve(second, 1);
    Deal third =
        deal(
            "Third",
            5,
            deal.getAvailableFrom(),
            deal.getAvailableUntil(),
            deal.getPickupStart(),
            deal.getPickupEnd());
    ApiException count = assertThrows(ApiException.class, () -> reserve(third, 1));
    assertEquals(ErrorCode.RESERVED_ORDER_LIMIT, count.getErrorCode());
  }

  @Test
  void allowsReservationCloseToPickupAfterCutoffRemoved() {
    // Không còn luật cutoff 15 phút: đặt sát giờ pickup vẫn thành công, miễn deal còn đang bán.
    Instant pickup = NOW.plusSeconds(900);
    Deal deal =
        deal(
            "Closing",
            3,
            NOW.minusSeconds(60),
            NOW.plusSeconds(3600),
            pickup,
            pickup.plusSeconds(1800));
    OrderResponse order = reserve(deal, 1);
    assertEquals(OrderStatus.RESERVED, order.orderStatus());
    assertEquals(2, deals.findByPublicId(deal.getPublicId()).orElseThrow().getRemainingQuantity());
    assertEquals(1, orders.count());
  }

  @Test
  void vietQrIsPerOrderAndBankDetailsRemainAvailableAsFallback() {
    banks.save(
        new MerchantBankAccount(merchant, "970415", "VietinBank", "113366668888", "NGUYEN VAN A"));
    Deal deal =
        deal(
            "Transfer",
            5,
            NOW.minusSeconds(60),
            NOW.plusSeconds(7200),
            NOW.plusSeconds(3600),
            NOW.plusSeconds(7200));
    OrderResponse order =
        service.reserve(
            buyer.getId().toString(),
            UUID.randomUUID(),
            new CreateOrderRequest(deal.getPublicId(), 2, PaymentMethod.BANK_TRANSFER));
    var instructions = payments.instructions(buyer.getId().toString(), order.orderId());

    assertNotNull(instructions.bank());
    assertTrue(
        instructions
            .qrImageUrl()
            .startsWith("https://img.vietqr.io/image/970415-113366668888-compact2.png"));
    assertTrue(instructions.qrImageUrl().contains("amount=100000"));
    assertTrue(instructions.qrImageUrl().contains(order.paymentReference()));
    assertEquals("113366668888", instructions.bank().accountNumber());

    ApiException inUse =
        assertThrows(
            ApiException.class,
            () ->
                bankService.upsertMyBankAccount(
                    merchantUser.getId().toString(),
                    new com.lootfast.backend.merchant.dto.UpdateBankAccountRequest(
                        "970436", "Vietcombank", "123456789", "NGUYEN VAN B")));
    assertEquals(ErrorCode.BANK_ACCOUNT_IN_USE, inUse.getErrorCode());
  }

  private OrderResponse reserve(Deal deal, int quantity) {
    return service.reserve(
        buyer.getId().toString(),
        UUID.randomUUID(),
        new CreateOrderRequest(deal.getPublicId(), quantity, PaymentMethod.CASH));
  }

  private Deal deal(
      String name,
      int stock,
      Instant saleStart,
      Instant saleEnd,
      Instant pickupStart,
      Instant pickupEnd) {
    return deals.saveAndFlush(
        new Deal(
            merchant,
            name,
            "MEAL",
            BigDecimal.valueOf(100_000),
            BigDecimal.valueOf(50_000),
            stock,
            "https://example.test/image.jpg",
            10,
            pickupStart,
            pickupEnd,
            saleStart,
            saleEnd,
            DealStatus.ACTIVE));
  }
}
