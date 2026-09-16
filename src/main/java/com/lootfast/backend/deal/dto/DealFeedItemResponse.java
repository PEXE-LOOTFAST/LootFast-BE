package com.lootfast.backend.deal.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DealFeedItemResponse(
        UUID dealId,
        String name,
        String categoryCode,
        BigDecimal originalPrice,
        BigDecimal discountedPrice,
        int discountPercent,
        int remainingQuantity,
        String primaryImageUrl,
        int preparationMinutes,
        Instant pickupStart,
        Instant pickupEnd,
        BigDecimal distanceKm,
        MerchantSummary merchant
) {
    public record MerchantSummary(
            UUID merchantId,
            String storeName,
            String addressLine,
            String ward,
            String district,
            String city,
            boolean verified
    ) {
    }
}
