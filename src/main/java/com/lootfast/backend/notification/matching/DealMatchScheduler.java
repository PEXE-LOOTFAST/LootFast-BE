package com.lootfast.backend.notification.matching;

import com.lootfast.backend.notification.service.MatchingDealNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Drains pending deal-match jobs. Safe to run on every instance thanks to SKIP LOCKED. */
@Component
@ConditionalOnProperty(
        name = "lootfast.notifications.matching.worker-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class DealMatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(DealMatchScheduler.class);
    private static final int MAX_BATCHES_PER_SWEEP = 20;

    private final MatchingDealNotificationService service;

    public DealMatchScheduler(MatchingDealNotificationService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "PT5S", initialDelayString = "PT10S", scheduler = "dealMatchTaskScheduler")
    public void sweep() {
        try {
            for (int i = 0; i < MAX_BATCHES_PER_SWEEP && service.processNextJob(); i++) {
                // keep draining while there is work, bounded so one sweep cannot run forever
            }
        } catch (MatchingDealNotificationService.BatchFailure failure) {
            log.error("Deal match job {} failed ({})", failure.dealId(),
                    failure.getCause().getClass().getSimpleName());
            try {
                service.recordFailure(failure);
            } catch (RuntimeException deferError) {
                log.error("Could not record failure for deal match job {} ({})", failure.dealId(),
                        deferError.getClass().getSimpleName());
            }
        } catch (RuntimeException exception) {
            log.error("Deal match sweep failed ({})", exception.getClass().getSimpleName());
        }
    }
}
