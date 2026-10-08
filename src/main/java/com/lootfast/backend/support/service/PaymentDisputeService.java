package com.lootfast.backend.support.service;

import com.lootfast.backend.notification.service.OrderNotificationService;
import com.lootfast.backend.order.entity.CustomerOrder;
import com.lootfast.backend.order.entity.PaymentMethod;
import com.lootfast.backend.order.entity.PaymentStatus;
import com.lootfast.backend.order.repository.OrderRepository;
import com.lootfast.backend.support.entity.SupportTicket;
import com.lootfast.backend.support.entity.TicketCategory;
import com.lootfast.backend.support.entity.TicketEvent;
import com.lootfast.backend.support.entity.TicketEventAction;
import com.lootfast.backend.support.entity.TicketOrigin;
import com.lootfast.backend.support.repository.SupportTicketRepository;
import com.lootfast.backend.support.repository.TicketEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * BR-PAY-08: chuyển khoản khách báo nhưng cửa hàng không xác nhận trong 24 giờ sau khi đơn kết thúc
 * sẽ tự mở một ticket PAYMENT_DISPUTE cho Admin, đồng thời nhắc cửa hàng xác nhận (phương án B).
 */
@Service
public class PaymentDisputeService {
  private static final long DISPUTE_DELAY_SECONDS = 24 * 3600;

  private final OrderRepository orders;
  private final SupportTicketRepository tickets;
  private final TicketEventRepository events;
  private final OrderNotificationService notifications;
  private final Clock clock;

  public PaymentDisputeService(
      OrderRepository orders,
      SupportTicketRepository tickets,
      TicketEventRepository events,
      OrderNotificationService notifications,
      Clock clock) {
    this.orders = orders;
    this.tickets = tickets;
    this.events = events;
    this.notifications = notifications;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<UUID> findCandidates() {
    return orders.findUnconfirmedTransferCandidates(
        clock.instant().minusSeconds(DISPUTE_DELAY_SECONDS));
  }

  /** Mở ticket cho một đơn; bỏ qua lặng lẽ nếu đơn đã đổi trạng thái từ lúc được xếp hàng. */
  @Transactional
  public void openDisputeFor(UUID orderPublicId) {
    CustomerOrder order = orders.findForUpdate(orderPublicId).orElse(null);
    if (order == null) return;
    Instant now = clock.instant();
    if (order.getPaymentMethod() != PaymentMethod.BANK_TRANSFER
        || order.getPaymentStatus() != PaymentStatus.REVIEW_REQUIRED
        || order.getFinalizedAt() == null
        || order.getFinalizedAt().isAfter(now.minusSeconds(DISPUTE_DELAY_SECONDS))) return;
    if (tickets.existsByOrderIdAndCategory(order.getId(), TicketCategory.PAYMENT_DISPUTE)) return;

    SupportTicket ticket =
        new SupportTicket(
            null,
            TicketOrigin.SYSTEM,
            TicketCategory.PAYMENT_DISPUTE,
            order,
            order.getMerchant(),
            "Chuyển khoản chưa được xác nhận",
            "Khách báo đã chuyển khoản cho đơn "
                + order.getPaymentReference()
                + " nhưng cửa hàng chưa xác nhận sau 24 giờ.",
            now);
    tickets.saveAndFlush(ticket);
    events.save(new TicketEvent(ticket, null, TicketEventAction.CREATED, "Hệ thống tự mở", now));
    notifications.remindMerchantToConfirmTransfer(order);
  }
}
