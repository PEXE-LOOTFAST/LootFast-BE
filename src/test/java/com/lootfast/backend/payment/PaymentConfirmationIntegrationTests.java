package com.lootfast.backend.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import com.lootfast.backend.order.entity.OrderStatus;
import com.lootfast.backend.order.entity.PaymentMethod;
import com.lootfast.backend.order.entity.PaymentStatus;
import com.lootfast.backend.order.service.MerchantOrderService;
import com.lootfast.backend.order.service.MerchantPickupConfirmationRateLimiter;
import com.lootfast.backend.order.service.OrderService;
import com.lootfast.backend.payment.dto.RejectPaymentRequest;
import com.lootfast.backend.payment.repository.PaymentEventRepository;
import com.lootfast.backend.payment.service.MerchantPaymentService;
import com.lootfast.backend.payment.service.PaymentTransactionService;
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
class PaymentConfirmationIntegrationTests {
  private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");

  @Autowired UserRepository users;
  @Autowired MerchantProfileRepository merchants;
  @Autowired MerchantBankAccountRepository banks;
  @Autowired DealRepository deals;
  @Autowired OrderService orderService;
  @Autowired PaymentTransactionService buyerPayments;
  @Autowired MerchantPaymentService merchantPayments;
  @Autowired MerchantOrderService merchantOrders;
  @Autowired PaymentEventRepository paymentEvents;

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
    banks.save(
        new MerchantBankAccount(
            merchant, "970415", "VietinBank", "113366668888", "NGUYEN VAN A"));
  }

  @Test
  void buyerReportsAndMerchantConfirmsBankTransferExactlyOnce() {
    OrderResponse order = reserve(PaymentMethod.BANK_TRANSFER);

    var reported = buyerPayments.reportBankTransfer(buyer.getId().toString(), order.orderId());
    assertEquals(PaymentStatus.REVIEW_REQUIRED, reported.paymentStatus());
    assertEquals(NOW, reported.paymentReportedAt());
    assertEquals(1, paymentEvents.countByOrderPublicId(order.orderId()));

    ApiException unpaidPickup =
        assertThrows(
            ApiException.class,
            () ->
                merchantOrders.confirm(
                    merchantUser.getId().toString(), order.pickupCode()));
    assertEquals(ErrorCode.PAYMENT_STATE_CONFLICT, unpaidPickup.getErrorCode());

    ApiException wrongStore =
        assertThrows(
            ApiException.class,
            () ->
                merchantPayments.confirmBankTransfer(
                    otherMerchantUser.getId().toString(), order.orderId()));
    assertEquals(ErrorCode.ORDER_NOT_FOUND, wrongStore.getErrorCode());

    var confirmed =
        merchantPayments.confirmBankTransfer(
            merchantUser.getId().toString(), order.orderId());
    assertEquals(PaymentStatus.CONFIRMED, confirmed.paymentStatus());
    assertEquals(order.paymentReference(), confirmed.paymentReference());
    assertEquals(NOW, confirmed.paymentConfirmedAt());
    assertEquals(2, paymentEvents.countByOrderPublicId(order.orderId()));

    ApiException paidCancellation =
        assertThrows(
            ApiException.class,
            () -> orderService.cancel(buyer.getId().toString(), order.orderId()));
    assertEquals(ErrorCode.PAYMENT_STATE_CONFLICT, paidCancellation.getErrorCode());

    var completed =
        merchantOrders.confirm(merchantUser.getId().toString(), order.pickupCode());
    assertEquals(OrderStatus.COMPLETED, completed.orderStatus());
    assertEquals(PaymentStatus.CONFIRMED, completed.paymentStatus());

    var replayAfterCompletion =
        merchantPayments.confirmBankTransfer(
            merchantUser.getId().toString(), order.orderId());
    assertEquals(OrderStatus.COMPLETED, replayAfterCompletion.orderStatus());
    assertEquals(PaymentStatus.CONFIRMED, replayAfterCompletion.paymentStatus());
    assertEquals(2, paymentEvents.countByOrderPublicId(order.orderId()));
  }

  @Test
  void merchantCanRejectReportAndBuyerCanReportAgain() {
    OrderResponse order = reserve(PaymentMethod.BANK_TRANSFER);
    buyerPayments.reportBankTransfer(buyer.getId().toString(), order.orderId());

    var rejected =
        merchantPayments.rejectBankTransfer(
            merchantUser.getId().toString(),
            order.orderId(),
            new RejectPaymentRequest("Amount or reference does not match"));
    assertEquals(PaymentStatus.UNPAID, rejected.paymentStatus());
    assertNull(rejected.paymentReportedAt());
    assertEquals(2, paymentEvents.countByOrderPublicId(order.orderId()));

    var reportedAgain =
        buyerPayments.reportBankTransfer(buyer.getId().toString(), order.orderId());
    assertEquals(PaymentStatus.REVIEW_REQUIRED, reportedAgain.paymentStatus());
    assertNotNull(reportedAgain.paymentReportedAt());
    assertEquals(3, paymentEvents.countByOrderPublicId(order.orderId()));
  }

  @Test
  void pickupConfirmationCollectsCashAndAuditsPaymentAtomically() {
    OrderResponse order = reserve(PaymentMethod.CASH);

    ApiException cashReport =
        assertThrows(
            ApiException.class,
            () -> buyerPayments.reportBankTransfer(buyer.getId().toString(), order.orderId()));
    assertEquals(ErrorCode.PAYMENT_STATE_CONFLICT, cashReport.getErrorCode());

    var completed =
        merchantOrders.confirm(merchantUser.getId().toString(), order.pickupQrPayload());
    assertEquals(OrderStatus.COMPLETED, completed.orderStatus());
    assertEquals(PaymentStatus.CONFIRMED, completed.paymentStatus());
    assertEquals(NOW, completed.paymentConfirmedAt());
    assertEquals(1, paymentEvents.countByOrderPublicId(order.orderId()));
  }

  private OrderResponse reserve(PaymentMethod method) {
    Deal deal =
        deals.saveAndFlush(
            new Deal(
                merchant,
                "Payment meal",
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
    return orderService.reserve(
        buyer.getId().toString(),
        UUID.randomUUID(),
        new CreateOrderRequest(deal.getPublicId(), 1, method));
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
    profile.updateLocation(
        "1 Test", "Ward", "District", "City", BigDecimal.TEN, BigDecimal.TEN);
    return merchants.save(profile);
  }
}
