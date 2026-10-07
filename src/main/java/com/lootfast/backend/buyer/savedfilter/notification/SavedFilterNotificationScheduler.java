package com.lootfast.backend.buyer.savedfilter.notification;

import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "lootfast.saved-filter-notifications.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class SavedFilterNotificationScheduler {
  private static final Logger log = LoggerFactory.getLogger(SavedFilterNotificationScheduler.class);
  private final SavedFilterNotificationService service;

  public SavedFilterNotificationScheduler(SavedFilterNotificationService service) {
    this.service = service;
  }

  @Scheduled(
      fixedDelayString = "PT10S",
      initialDelayString = "PT10S",
      scheduler = "savedFilterNotificationScheduler")
  public void sweep() {
    try {
      for (int i = 0; i < 20 && service.processBatch(); i++) {}
    } catch (SavedFilterNotificationService.BatchFailure e) {
      log.error(
          "Saved filter job {} failed ({})", e.dealId(), e.getCause().getClass().getSimpleName());
      try {
        service.defer(e);
      } catch (RuntimeException deferError) {
        log.error(
            "Could not defer saved filter job {} ({})",
            e.dealId(),
            deferError.getClass().getSimpleName());
      }
    } catch (RuntimeException e) {
      log.error("Saved filter notification batch failed ({})", e.getClass().getSimpleName());
    }
  }
}
