package com.lootfast.backend.notification.matching;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableConfigurationProperties(MatchingProperties.class)
public class DealMatchConfig {

    /** Dedicated thread so slow matching never delays other scheduled jobs (and vice versa). */
    @Bean("dealMatchTaskScheduler")
    ThreadPoolTaskScheduler dealMatchTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("deal-match-");
        return scheduler;
    }
}
