package com.lootfast.backend.deal.dto;

import io.swagger.v3.oas.annotations.media.Schema;

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
        MerchantSummary merchant,
        @Schema(description = "true khi đây là deal minh họa, không có thật ngoài đời và không thể đặt. "
                + "App cần hiện badge DEMO và ẩn nút đặt/yêu thích.")
        boolean demo,
        @Schema(description = "Thông báo hiển thị cho deal minh họa; null với deal thật.")
        String demoNotice
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
