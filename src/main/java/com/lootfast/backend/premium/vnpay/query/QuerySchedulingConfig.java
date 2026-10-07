package com.lootfast.backend.premium.vnpay.query;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@ConditionalOnProperty(name = "lootfast.vnpay.query.enabled", havingValue = "true")
public class QuerySchedulingConfig {
  @Bean(name = "vnpayQueryScheduler")
  public ThreadPoolTaskScheduler scheduler() {
    var scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix("vnpay-query-");
    scheduler.setWaitForTasksToCompleteOnShutdown(true);
    scheduler.setAwaitTerminationSeconds(45);
    return scheduler;
  }
}
