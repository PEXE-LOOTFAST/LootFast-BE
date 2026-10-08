package com.lootfast.backend.notification.outbox;

import org.springframework.context.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class EmailOutboxSchedulingConfig {
  /** Email network calls cannot delay pickup reminders or the no-show sweeper. */
  @Bean("emailOutboxScheduler")
  ThreadPoolTaskScheduler emailOutboxScheduler() {
    var scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix("email-outbox-");
    return scheduler;
  }
}
