package com.lootfast.backend.deal.demo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A generated, non-existent deal shown only to illustrate the app in areas without real deals.
 * It is never persisted, so it cannot be ordered, paid for, favourited or reviewed.
 */
public record DemoDeal(
        UUID id,
        UUID storeId,
        String name,
        String storeName,
        String categoryCode,
        BigDecimal originalPrice,
        BigDecimal discountedPrice,
        int remainingQuantity,
        int preparationMinutes,
        Instant availableFrom,
        Instant pickupStart,
        Instant pickupEnd,
        double latitude,
        double longitude
) {

    public static final String NOTICE = "Deal minh họa để trải nghiệm ứng dụng: cửa hàng và món ăn "
            + "này không có thật ngoài đời, không thể đặt hoặc nhận.";
    public static final String ADDRESS_LINE = "Địa điểm minh họa - không có thật";

    /** Demo deals stay listed until pickup ends, like a real deal whose availability ends then. */
    public Instant availableUntil() {
        return pickupEnd;
    }

    /** Mirrors the real feed's visibility rule: availableFrom <= now < pickupEnd. */
    public boolean visibleAt(Instant now) {
        return !availableFrom.isAfter(now) && pickupEnd.isAfter(now);
    }
}
