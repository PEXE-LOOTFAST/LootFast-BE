package com.lootfast.backend.deal.demo;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.ZoneId;

/**
 * Illustrative ("demo") deals generated around a buyer when no real deal matches their area.
 *
 * <p>{@code enabled} defaults to false, so demo deals appear only where the environment opts in
 * with {@code DEMO_DEALS_ENABLED=true}.
 */
@ConfigurationProperties(prefix = "lootfast.demo-deals")
public record DemoDealProperties(
        Boolean enabled,
        Integer maxPerFeed,
        ZoneId zone
) {

    public DemoDealProperties {
        enabled = Boolean.TRUE.equals(enabled);
        maxPerFeed = maxPerFeed == null || maxPerFeed < 1 || maxPerFeed > 5 ? 2 : maxPerFeed;
        zone = zone == null ? ZoneId.of("Asia/Ho_Chi_Minh") : zone;
    }
}
