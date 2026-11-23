package com.lootfast.backend.notification.realtime;

import com.lootfast.backend.notification.event.NotificationCreatedEvent;
import com.lootfast.backend.notification.event.UnreadCountChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Pushes notification changes to WebSocket clients <b>after</b> the database transaction
 * commits. Pushing earlier could announce a notification that is later rolled back, and doing
 * network I/O while still holding row locks would slow every writer down.
 */
@Component
public class NotificationPushListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationPushListener.class);

    private final UserEventGateway gateway;

    public NotificationPushListener(UserEventGateway gateway) {
        this.gateway = gateway;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCreated(NotificationCreatedEvent event) {
        push(event.recipientUserId(),
                new RealtimeEnvelope(RealtimeEnvelope.NOTIFICATION_CREATED, event.notification()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUnreadCountChanged(UnreadCountChangedEvent event) {
        push(event.recipientUserId(), new RealtimeEnvelope(
                RealtimeEnvelope.UNREAD_COUNT_CHANGED,
                new RealtimeEnvelope.UnreadCount(event.unreadCount())
        ));
    }

    private void push(Long userId, RealtimeEnvelope envelope) {
        try {
            gateway.send(userId, envelope);
        } catch (RuntimeException exception) {
            // Never let realtime delivery break the business flow that already committed.
            log.warn("Realtime push {} failed ({})", envelope.event(), exception.getClass().getSimpleName());
        }
    }
}
