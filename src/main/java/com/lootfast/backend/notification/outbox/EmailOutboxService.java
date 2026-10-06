package com.lootfast.backend.notification.outbox;

import com.lootfast.backend.notification.repository.UserNotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Clock;
import java.util.UUID;

@Service
public class EmailOutboxService {
    private final EmailOutboxRepository outbox;
    private final UserNotificationRepository notifications;
    private final Clock clock;
    public EmailOutboxService(EmailOutboxRepository outbox, UserNotificationRepository notifications, Clock clock) {
        this.outbox = outbox; this.notifications = notifications; this.clock = clock;
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(UUID notificationId, String email, String subject, String htmlBody, EmailPreferenceCategory category) {
        if (outbox.existsById(notificationId)) return;
        var notification = notifications.findByPublicId(notificationId)
                .orElseThrow(() -> new IllegalStateException("Cannot enqueue email without its notification"));
        outbox.save(new EmailOutbox(notification, email, subject, htmlBody, category, clock.instant()));
    }
}
