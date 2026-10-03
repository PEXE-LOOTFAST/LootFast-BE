package com.lootfast.backend.notification.service;

import com.lootfast.backend.notification.entity.*;
import com.lootfast.backend.notification.event.OrderEmailEvent;
import com.lootfast.backend.notification.repository.*;
import com.lootfast.backend.order.entity.CustomerOrder;
import com.lootfast.backend.user.entity.User;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Invoked while the caller holds the order lock (new orders are private to their transaction). */
@Service
public class OrderNotificationService {
    private final UserNotificationRepository notifications;
    private final NotificationPreferenceRepository preferences;
    private final ApplicationEventPublisher events;

    public OrderNotificationService(UserNotificationRepository notifications,
            NotificationPreferenceRepository preferences, ApplicationEventPublisher events) {
        this.notifications = notifications;
        this.preferences = preferences;
        this.events = events;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyBoth(CustomerOrder order, NotificationType type) {
        notifyBoth(order, type, order.getPublicId());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyBoth(CustomerOrder order, NotificationType type, java.util.UUID occurrenceKey) {
        notifyRecipient(order, order.getBuyer(), type, false, occurrenceKey);
        if (!order.getBuyer().getId().equals(order.getMerchant().getUser().getId())) {
            notifyRecipient(order, order.getMerchant().getUser(), type, true, occurrenceKey);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void remindBuyer(CustomerOrder order) {
        notifyRecipient(order, order.getBuyer(), NotificationType.PICKUP_REMINDER, false, order.getPublicId());
    }

    /** Nhắc cửa hàng xác nhận chuyển khoản còn treo (task 045). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void remindMerchantToConfirmTransfer(CustomerOrder order) {
        notifyRecipient(order, order.getMerchant().getUser(),
                NotificationType.PAYMENT_CONFIRMATION_REQUEST, true, order.getPublicId());
    }

    private void notifyRecipient(CustomerOrder order, User recipient, NotificationType type, boolean merchant, java.util.UUID occurrenceKey) {
        var preference = preferences.findByUserId(recipient.getId()).orElse(null);
        boolean enabled = preference == null || (type == NotificationType.PICKUP_REMINDER
                ? preference.isPickupRemindersEnabled()
                : merchant ? preference.isMerchantOrderUpdatesEnabled() : preference.isBuyerOrderUpdatesEnabled());
        if (!enabled || notifications.existsByRecipientIdAndTypeAndOrderPublicIdAndOccurrenceKey(
                recipient.getId(), type, order.getPublicId(), occurrenceKey)) return;
        String title = switch (type) {
            case ORDER_RESERVED -> merchant ? "Có đơn đặt mới" : "Đặt đơn thành công";
            case TRANSFER_REPORTED -> "Chuyển khoản đang chờ xác nhận";
            case PAYMENT_CONFIRMED -> "Thanh toán đã được xác nhận";
            case PAYMENT_REJECTED -> "Chưa xác nhận được chuyển khoản";
            case ORDER_CANCELLED -> "Đơn hàng đã hủy";
            case ORDER_COMPLETED -> "Đơn hàng đã giao thành công";
            case ORDER_NO_SHOW -> "Đơn hàng không được nhận đúng hạn";
            case PICKUP_REMINDER -> "Sắp đến giờ nhận món";
            case PAYMENT_CONFIRMATION_REQUEST -> "Nhắc xác nhận chuyển khoản";
            default -> throw new IllegalArgumentException("Not an order notification: " + type);
        };
        String message = "%s — %s, %d phần tại %s.".formatted(title,
                order.getDealName(), order.getQuantity(), order.getMerchant().getStoreName());
        if (type == NotificationType.PICKUP_REMINDER) {
            var format = java.time.format.DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")
                    .withZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
            message += " Nhận từ " + format.format(order.getPickupStart()) + " đến "
                    + format.format(order.getPickupEnd()) + ".";
        }
        var notification = notifications.save(new UserNotification(recipient, type, order, occurrenceKey, title, message));
        if (preference != null && preference.isEmailEnabled()) {
            events.publishEvent(new OrderEmailEvent(recipient.getEmail(), notification.getPublicId(), title, message));
        }
    }
}
