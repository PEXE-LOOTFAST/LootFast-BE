package com.lootfast.backend.deal.dto;

import com.lootfast.backend.deal.entity.DealStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DealDetailResponse(
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
        Instant availableFrom,
        Instant availableUntil,
        DealStatus status,
        BigDecimal distanceKm,
        long inventoryVersion,
        Instant updatedAt,
        RatingSummary rating,
        MerchantDetail merchant,
        @Schema(description = "true khi đây là deal minh họa, không có thật ngoài đời và không thể đặt. "
                + "App cần hiện badge DEMO và ẩn nút đặt/yêu thích.")
        boolean demo,
        @Schema(description = "Thông báo hiển thị cho deal minh họa; null với deal thật.")
        String demoNotice
) {
    public record RatingSummary(BigDecimal averageRating, long reviewCount) {
    }

    public record MerchantDetail(
            UUID merchantId,
            String storeName,
            String logoUrl,
            String addressLine,
            String ward,
            String district,
            String city,
            boolean verified
    ) {
    }
}
