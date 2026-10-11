package com.lootfast.backend.buyer.savedfilter.notification;

import org.springframework.context.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class SavedFilterSchedulingConfig {
  @Bean("savedFilterNotificationTaskScheduler")
  ThreadPoolTaskScheduler scheduler() {
    var scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix("saved-filter-notifications-");
    return scheduler;
  }
}
