package com.lootfast.backend.support;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import com.lootfast.backend.deal.entity.Deal;
import com.lootfast.backend.deal.entity.DealStatus;
import com.lootfast.backend.deal.repository.DealRepository;
import com.lootfast.backend.merchant.entity.MerchantProfile;
import com.lootfast.backend.merchant.repository.MerchantProfileRepository;
import com.lootfast.backend.notification.entity.NotificationType;
import com.lootfast.backend.notification.repository.UserNotificationRepository;
import com.lootfast.backend.order.entity.CustomerOrder;
import com.lootfast.backend.order.entity.PaymentMethod;
import com.lootfast.backend.order.repository.OrderRepository;
import com.lootfast.backend.support.entity.TicketCategory;
import com.lootfast.backend.support.repository.SupportTicketRepository;
import com.lootfast.backend.support.service.PaymentDisputeService;
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
class PaymentDisputeServiceTests {
  private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
  private static final long HOUR = 3600;

  @Autowired UserRepository users;
  @Autowired MerchantProfileRepository merchants;
  @Autowired DealRepository deals;
  @Autowired OrderRepository orders;
  @Autowired SupportTicketRepository tickets;
  @Autowired UserNotificationRepository userNotifications;
  @Autowired PaymentDisputeService service;

  @MockitoBean Clock clock;

  User buyer;
  User merchantUser;
  MerchantProfile merchant;

  @BeforeEach
  void setup() {
    when(clock.instant()).thenReturn(NOW);
    buyer = user("Buyer", UserRole.BUYER);
    merchantUser = user("Merchant", UserRole.MERCHANT);
    merchant = new MerchantProfile(merchantUser);
    merchant.updateStoreInformation("Test store", "description", "MEAL", null, null);
    merchant.updateLocation("1 Test", "Ward", "District", "City", BigDecimal.TEN, BigDecimal.TEN);
    merchants.save(merchant);
  }

  @Test
  void opensDisputeTicketAndNotifiesMerchant() {
    CustomerOrder order = unconfirmedTransferOrder();
    when(clock.instant()).thenReturn(NOW.plusSeconds(25 * HOUR));

    assertTrue(service.findCandidates().contains(order.getPublicId()));
    service.openDisputeFor(order.getPublicId());

    assertTrue(tickets.existsByOrderIdAndCategory(order.getId(), TicketCategory.PAYMENT_DISPUTE));
    assertTrue(
        userNotifications.existsByRecipientIdAndTypeAndOrderPublicIdAndOccurrenceKey(
            merchantUser.getId(),
            NotificationType.PAYMENT_CONFIRMATION_REQUEST,
            order.getPublicId(),
            order.getPublicId()));
  }

  @Test
  void doesNotCreateDuplicateTicket() {
    CustomerOrder order = unconfirmedTransferOrder();
    when(clock.instant()).thenReturn(NOW.plusSeconds(25 * HOUR));

    service.openDisputeFor(order.getPublicId());
    service.openDisputeFor(order.getPublicId());

    assertEquals(1, tickets.count());
  }

  @Test
  void skipsWhenTransferAlreadyConfirmed() {
    CustomerOrder order = confirmedTransferOrder();
    when(clock.instant()).thenReturn(NOW.plusSeconds(25 * HOUR));

    assertFalse(service.findCandidates().contains(order.getPublicId()));
    service.openDisputeFor(order.getPublicId());

    assertEquals(0, tickets.count());
  }

  @Test
  void skipsBeforeTwentyFourHours() {
    CustomerOrder order = unconfirmedTransferOrder();
    when(clock.instant()).thenReturn(NOW.plusSeconds(HOUR)); // mới 1 giờ

    assertFalse(service.findCandidates().contains(order.getPublicId()));
    service.openDisputeFor(order.getPublicId());

    assertEquals(0, tickets.count());
  }

  private CustomerOrder unconfirmedTransferOrder() {
    CustomerOrder order = bankTransferOrder();
    order.reportBankTransfer(NOW); // REVIEW_REQUIRED
    order.complete(NOW); // kết thúc, finalizedAt = NOW
    return orders.saveAndFlush(order);
  }

  private CustomerOrder confirmedTransferOrder() {
    CustomerOrder order = bankTransferOrder();
    order.reportBankTransfer(NOW);
    order.confirmBankTransfer(merchantUser, NOW); // CONFIRMED
    order.complete(NOW);
    return orders.saveAndFlush(order);
  }

  private CustomerOrder bankTransferOrder() {
    return new CustomerOrder(
        buyer, activeDeal(), 1, PaymentMethod.BANK_TRANSFER, UUID.randomUUID(), "fp", NOW);
  }

  private User user(String first, UserRole role) {
    return users.save(
        new User(
            first, "T", UUID.randomUUID() + "@test.local",
            null, null, role, UserStatus.VERIFIED));
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
