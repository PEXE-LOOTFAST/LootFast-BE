package com.lootfast.backend.notification.event;

import com.lootfast.backend.common.email.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
class MatchingDealEmailListener {

    private static final Logger log = LoggerFactory.getLogger(MatchingDealEmailListener.class);

    private final EmailSender emailSender;

    MatchingDealEmailListener(EmailSender emailSender) {
        this.emailSender = emailSender;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void send(MatchingDealEmailEvent event) {
        try {
            emailSender.sendMatchingDeal(
                    event.recipientEmail(),
                    event.notificationId(),
                    event.dealName(),
                    event.storeName(),
                    event.originalPrice(),
                    event.discountedPrice()
            );
        } catch (RuntimeException exception) {
            log.error("Failed to schedule matching-deal email", exception);
        }
    }
}
