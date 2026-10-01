package com.lootfast.backend.notification.event;

import com.lootfast.backend.common.email.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;

@Component
class OrderEmailListener {
    private static final Logger log = LoggerFactory.getLogger(OrderEmailListener.class);
    private final EmailSender emailSender;
    OrderEmailListener(EmailSender emailSender) { this.emailSender = emailSender; }
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void send(OrderEmailEvent event) {
        try {
            emailSender.sendOrderNotification(event.email(), event.notificationId(), event.title(), event.message());
        } catch (RuntimeException exception) {
            log.error("Could not schedule order notification email {}", event.notificationId(), exception);
        }
    }
}
