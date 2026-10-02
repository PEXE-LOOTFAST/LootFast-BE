package com.lootfast.backend.notification.scheduler;

import com.lootfast.backend.notification.service.PickupReminderService;
import org.slf4j.*;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
@ConditionalOnProperty(name = "lootfast.pickup-reminders.enabled", havingValue = "true", matchIfMissing = true)
public class PickupReminderScheduler {
    private static final Logger log = LoggerFactory.getLogger(PickupReminderScheduler.class);
    private final PickupReminderService service;
    public PickupReminderScheduler(PickupReminderService service) { this.service = service; }
    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
    public void sweep() {
        try {
            for (var id : service.candidates()) {
                try { service.remind(id); }
                catch (RuntimeException exception) { log.warn("Pickup reminder failed for {}", id, exception); }
            }
        } catch (RuntimeException exception) { log.warn("Could not list pickup reminders", exception); }
    }
}
