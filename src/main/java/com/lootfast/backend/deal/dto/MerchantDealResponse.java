package com.lootfast.backend.deal.dto;

import com.lootfast.backend.deal.entity.Deal;
import com.lootfast.backend.deal.entity.DealImage;
import com.lootfast.backend.deal.entity.DealStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MerchantDealResponse(
        UUID dealId,
        String name,
        String categoryCode,
        BigDecimal originalPrice,
        BigDecimal discountedPrice,
        int discountPercent,
        int totalQuantity,
        int remainingQuantity,
        int committedQuantity,
        String primaryImageUrl,
        List<DealImageResponse> images,
        int preparationMinutes,
        Instant saleStartsAt,
        Instant saleEndsAt,
        Instant pickupStartsAt,
        Instant pickupEndsAt,
        DealStatus status,
        long version,
        Instant createdAt,
        Instant updatedAt,
        Instant hiddenAt,
        Instant removedAt,
        String removalReason
) {
    public static MerchantDealResponse from(Deal deal, List<DealImage> images) {
        BigDecimal discount = deal.getOriginalPrice()
                .subtract(deal.getDiscountedPrice())
                .multiply(BigDecimal.valueOf(100))
                .divide(deal.getOriginalPrice(), 0, RoundingMode.DOWN);
        return new MerchantDealResponse(
                deal.getPublicId(),
                deal.getName(),
                deal.getCategoryCode(),
                deal.getOriginalPrice(),
                deal.getDiscountedPrice(),
                discount.intValueExact(),
                deal.getTotalQuantity(),
                deal.getRemainingQuantity(),
                deal.getTotalQuantity() - deal.getRemainingQuantity(),
                deal.getPrimaryImageUrl(),
                images.stream().map(DealImageResponse::from).toList(),
                deal.getPreparationMinutes(),
                deal.getAvailableFrom(),
                deal.getAvailableUntil(),
                deal.getPickupStart(),
                deal.getPickupEnd(),
                deal.getStatus(),
                deal.getVersion(),
                deal.getCreatedAt(),
                deal.getUpdatedAt(),
                deal.getHiddenAt(),
                deal.getRemovedAt(),
                deal.getRemovalReason()
        );
    }
}
