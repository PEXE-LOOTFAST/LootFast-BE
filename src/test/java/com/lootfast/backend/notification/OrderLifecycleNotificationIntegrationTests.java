package com.lootfast.backend.notification;

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
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class OrderLifecycleNotificationIntegrationTests {
  private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");

  @Autowired UserRepository users;
  @Autowired MerchantProfileRepository merchants;
  @Autowired DealRepository deals;
  @Autowired OrderRepository orders;
  @Autowired OrderService service;

  @MockitoBean com.lootfast.backend.common.email.EmailSender emailSender;
  @MockitoBean Clock clock;
  @MockitoBean com.lootfast.backend.order.service.MerchantPickupConfirmationRateLimiter rateLimiter;

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

  @Autowired com.lootfast.backend.notification.outbox.EmailOutboxRepository outbox;
  @Autowired com.lootfast.backend.notification.repository.UserNotificationRepository notifications;
  @Autowired com.lootfast.backend.notification.repository.NotificationPreferenceRepository preferences;
  @Autowired com.lootfast.backend.notification.service.PickupReminderService reminders;
  @Autowired com.lootfast.backend.order.service.MerchantOrderService fulfillment;
  @Autowired com.lootfast.backend.payment.service.PaymentTransactionService payments;
  @Autowired com.lootfast.backend.payment.service.MerchantPaymentService merchantPayments;
  @Autowired com.lootfast.backend.merchant.repository.MerchantBankAccountRepository banks;

  private long count(com.lootfast.backend.notification.entity.NotificationType type) {
    return notifications.findAll().stream().filter(n -> n.getType() == type).count();
  }

  @Test
  void reservationReplayAndCancellationNotifyBothExactlyOnce() {
    var deal = activeDeal(5);
    var key = UUID.randomUUID();
    var request = new CreateOrderRequest(deal.getPublicId(), 1, PaymentMethod.CASH);
    var response = service.reserve(buyer.getId().toString(), key, request);
    service.reserve(buyer.getId().toString(), key, request);
    assertEquals(2, count(com.lootfast.backend.notification.entity.NotificationType.ORDER_RESERVED));
    assertTrue(notifications.findAll().stream().allMatch(n -> response.orderId().equals(n.getOrderPublicId())));
    service.cancel(buyer.getId().toString(), response.orderId());
    assertEquals(2, count(com.lootfast.backend.notification.entity.NotificationType.ORDER_CANCELLED));
    assertThrows(ApiException.class, () -> service.cancel(buyer.getId().toString(), response.orderId()));
    assertEquals(4, notifications.count());
  }

  @Test
  void reminderBoundaryDedupeAndFinalizationRecheck() {
    var response = reserve(activeDeal(5), 1);
    assertTrue(reminders.candidates().isEmpty());
    when(clock.instant()).thenReturn(NOW.plusSeconds(1800));
    assertEquals(java.util.List.of(response.orderId()), reminders.candidates());
    reminders.remind(response.orderId());
    reminders.remind(response.orderId());
    assertEquals(1, count(com.lootfast.backend.notification.entity.NotificationType.PICKUP_REMINDER));
    assertTrue(reminders.candidates().isEmpty());
  }

  @Test
  void cancelledAndLateOrdersDoNotReceiveReminders() {
    var response = reserve(activeDeal(5), 1);
    service.cancel(buyer.getId().toString(), response.orderId());
    when(clock.instant()).thenReturn(NOW.plusSeconds(1800));
    reminders.remind(response.orderId());
    assertTrue(reminders.candidates().isEmpty());
    assertEquals(0, count(com.lootfast.backend.notification.entity.NotificationType.PICKUP_REMINDER));
  }

  @Test
  void categoryPreferencesAreIndependentAndExcludeDisabledCandidates() {
    var buyerPreference = new com.lootfast.backend.notification.entity.NotificationPreference(buyer);
    buyerPreference.update(true, false, true, false, false);
    preferences.saveAndFlush(buyerPreference);
    var response = reserve(activeDeal(5), 1);
    assertEquals(1, notifications.count()); // merchant remains enabled
    when(clock.instant()).thenReturn(NOW.plusSeconds(1800));
    assertTrue(reminders.candidates().isEmpty());
    reminders.remind(response.orderId());
    assertEquals(0, count(com.lootfast.backend.notification.entity.NotificationType.PICKUP_REMINDER));
  }

  @Test
  void bankTransferReportAndConfirmationReplayAreDeduplicated() {
    banks.saveAndFlush(new com.lootfast.backend.merchant.entity.MerchantBankAccount(
        merchant, "970415", "VietinBank", "113366668888", "TEST MERCHANT"));
    var deal = activeDeal(5);
    var response = service.reserve(buyer.getId().toString(), UUID.randomUUID(),
        new CreateOrderRequest(deal.getPublicId(), 1, PaymentMethod.BANK_TRANSFER));
    payments.reportBankTransfer(buyer.getId().toString(), response.orderId());
    payments.reportBankTransfer(buyer.getId().toString(), response.orderId());
    assertEquals(2, count(com.lootfast.backend.notification.entity.NotificationType.TRANSFER_REPORTED));
    merchantPayments.confirmBankTransfer(merchant.getUser().getId().toString(), response.orderId());
    merchantPayments.confirmBankTransfer(merchant.getUser().getId().toString(), response.orderId());
    assertEquals(2, count(com.lootfast.backend.notification.entity.NotificationType.PAYMENT_CONFIRMED));
    fulfillment.confirm(merchant.getUser().getId().toString(), response.pickupCode());
    assertEquals(2, count(com.lootfast.backend.notification.entity.NotificationType.ORDER_COMPLETED));
  }

  @Test
  void rejectionAndNoShowCreateLifecycleNotifications() {
    banks.saveAndFlush(new com.lootfast.backend.merchant.entity.MerchantBankAccount(
        merchant, "970415", "VietinBank", "113366668888", "TEST MERCHANT"));
    var deal = activeDeal(5);
    var response = service.reserve(buyer.getId().toString(), UUID.randomUUID(),
        new CreateOrderRequest(deal.getPublicId(), 1, PaymentMethod.BANK_TRANSFER));
    payments.reportBankTransfer(buyer.getId().toString(), response.orderId());
    merchantPayments.rejectBankTransfer(merchant.getUser().getId().toString(), response.orderId(),
        new com.lootfast.backend.payment.dto.RejectPaymentRequest("No matching transfer"));
    assertEquals(2, count(com.lootfast.backend.notification.entity.NotificationType.PAYMENT_REJECTED));
    when(clock.instant()).thenReturn(NOW.plusSeconds(1));
    payments.reportBankTransfer(buyer.getId().toString(), response.orderId());
    merchantPayments.rejectBankTransfer(merchant.getUser().getId().toString(), response.orderId(),
        new com.lootfast.backend.payment.dto.RejectPaymentRequest("Still no matching transfer"));
    assertEquals(4, count(com.lootfast.backend.notification.entity.NotificationType.TRANSFER_REPORTED));
    assertEquals(4, count(com.lootfast.backend.notification.entity.NotificationType.PAYMENT_REJECTED));
    when(clock.instant()).thenReturn(NOW.plusSeconds(9001));
    service.markNoShow(response.orderId());
    service.markNoShow(response.orderId());
    assertEquals(2, count(com.lootfast.backend.notification.entity.NotificationType.ORDER_NO_SHOW));
  }

  @Test
  void rolledBackOrderDoesNotLeaveNotificationsOrSendEmail() {
    var preference = new com.lootfast.backend.notification.entity.NotificationPreference(buyer);
    preference.update(true, true, true, true, true);
    preferences.saveAndFlush(preference);
    reserve(activeDeal(5), 1);
    assertEquals(2, notifications.count());
    assertEquals(1, outbox.count());
    org.springframework.test.context.transaction.TestTransaction.flagForRollback();
    org.springframework.test.context.transaction.TestTransaction.end();
    org.springframework.test.context.transaction.TestTransaction.start();
    assertEquals(0, notifications.count());
    assertEquals(0, outbox.count());
    org.mockito.Mockito.verify(emailSender, org.mockito.Mockito.never()).sendOrderNotification(
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  void cashCollectionSendsPaymentAndCompletionNotifications() {
    var response = reserve(activeDeal(5), 1);
    fulfillment.confirm(merchant.getUser().getId().toString(), response.pickupCode());
    assertEquals(2, count(com.lootfast.backend.notification.entity.NotificationType.PAYMENT_CONFIRMED));
    assertEquals(2, count(com.lootfast.backend.notification.entity.NotificationType.ORDER_COMPLETED));
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
