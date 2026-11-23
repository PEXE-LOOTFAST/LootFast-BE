package com.lootfast.backend.notification.matching;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.time.ZoneId;

/**
 * Tuning for preference-based "matching deal" notifications.
 *
 * <p>Each preference signal adds its weight to a score; a buyer is notified only when the score
 * reaches {@code threshold}. With the defaults, a single strong food preference (favourite
 * store, category, dish or repeated orders) is enough, while "nearby" or "big discount" alone is
 * not; the two weak signals together are.
 */
@ConfigurationProperties(prefix = "lootfast.notifications.matching")
public record MatchingProperties(
        Weights weights,
        Integer threshold,
        Integer dailyLimit,
        Duration orderHistoryWindow,
        Integer orderHistoryMinOrders,
        Integer batchSize,
        ZoneId zone
) {

    public MatchingProperties {
        weights = weights == null ? new Weights(null, null, null, null, null, null) : weights;
        threshold = positiveOr(threshold, 30);
        dailyLimit = positiveOr(dailyLimit, 5);
        orderHistoryWindow = orderHistoryWindow == null || orderHistoryWindow.isNegative()
                || orderHistoryWindow.isZero() ? Duration.ofDays(30) : orderHistoryWindow;
        orderHistoryMinOrders = positiveOr(orderHistoryMinOrders, 2);
        batchSize = batchSize == null || batchSize < 1 || batchSize > 1000 ? 500 : batchSize;
        zone = zone == null ? ZoneId.of("Asia/Ho_Chi_Minh") : zone;
    }

    public record Weights(
            Integer favoriteStore,
            Integer favoriteCategory,
            Integer favoriteItem,
            Integer orderHistory,
            Integer nearbyArea,
            Integer bigDiscount
    ) {
        public Weights {
            favoriteStore = nonNegativeOr(favoriteStore, 50);
            favoriteCategory = nonNegativeOr(favoriteCategory, 40);
            favoriteItem = nonNegativeOr(favoriteItem, 40);
            orderHistory = nonNegativeOr(orderHistory, 30);
            nearbyArea = nonNegativeOr(nearbyArea, 20);
            bigDiscount = nonNegativeOr(bigDiscount, 10);
        }
    }

    private static Integer positiveOr(Integer value, int fallback) {
        return value == null || value < 1 ? fallback : value;
    }

    private static Integer nonNegativeOr(Integer value, int fallback) {
        return value == null || value < 0 ? fallback : value;
    }
}
