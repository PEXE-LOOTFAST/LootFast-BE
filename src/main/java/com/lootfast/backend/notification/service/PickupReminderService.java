package com.lootfast.backend.notification.service;

import com.lootfast.backend.order.entity.OrderStatus;
import com.lootfast.backend.order.repository.OrderRepository;
import com.lootfast.backend.config.DatabaseLockTimeout;
import java.time.Clock;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PickupReminderService {
    private final OrderRepository orders;
    private final OrderNotificationService notifications;
    private final Clock clock;
    private final DatabaseLockTimeout lockTimeout;
    public PickupReminderService(OrderRepository orders, OrderNotificationService notifications,
            Clock clock, DatabaseLockTimeout lockTimeout) {
        this.orders = orders; this.notifications = notifications; this.clock = clock; this.lockTimeout = lockTimeout;
    }
    @Transactional(readOnly = true)
    public List<UUID> candidates() {
        var now = clock.instant();
        return orders.findPickupReminderCandidates(now, now.plusSeconds(1800), PageRequest.of(0, 500));
    }
    @Transactional(timeout = 5)
    public void remind(UUID orderId) {
        lockTimeout.apply();
        var order = orders.findForUpdate(orderId).orElse(null);
        var now = clock.instant();
        if (order == null || order.getStatus() != OrderStatus.RESERVED
                || !order.getPickupStart().isAfter(now)
                || order.getPickupStart().isAfter(now.plusSeconds(1800))) return;
        notifications.remindBuyer(order);
    }
}
