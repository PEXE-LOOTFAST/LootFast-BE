package com.lootfast.backend.deal.service;

import com.lootfast.backend.deal.dto.DealDetailResponse;
import com.lootfast.backend.deal.dto.DealFeedItemResponse;
import com.lootfast.backend.deal.entity.Deal;
import com.lootfast.backend.merchant.entity.MerchantProfile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class DealResponseMapper {

    public DealFeedItemResponse toFeedItem(Deal deal, BigDecimal distanceKm) {
        MerchantProfile merchant = deal.getMerchant();
        return new DealFeedItemResponse(
                deal.getPublicId(),
                deal.getName(),
                deal.getCategoryCode(),
                deal.getOriginalPrice(),
                deal.getDiscountedPrice(),
                discountPercent(deal),
                deal.getRemainingQuantity(),
                deal.getPrimaryImageUrl(),
                deal.getPreparationMinutes(),
                deal.getPickupStart(),
                deal.getPickupEnd(),
                distanceKm,
                new DealFeedItemResponse.MerchantSummary(
                        merchant.getUser().getPublicId(),
                        merchant.getStoreName(),
                        merchant.getAddressLine(),
                        merchant.getWard(),
                        merchant.getDistrict(),
                        merchant.getCity(),
                        true
                )
        );
    }

    public DealDetailResponse toDetail(Deal deal, BigDecimal distanceKm) {
        MerchantProfile merchant = deal.getMerchant();
        return new DealDetailResponse(
                deal.getPublicId(),
                deal.getName(),
                deal.getCategoryCode(),
                deal.getOriginalPrice(),
                deal.getDiscountedPrice(),
                discountPercent(deal),
                deal.getRemainingQuantity(),
                deal.getPrimaryImageUrl(),
                deal.getPreparationMinutes(),
                deal.getPickupStart(),
                deal.getPickupEnd(),
                deal.getAvailableFrom(),
                deal.getAvailableUntil(),
                deal.getStatus(),
                distanceKm,
                deal.getVersion(),
                deal.getUpdatedAt(),
                new DealDetailResponse.RatingSummary(null, 0),
                new DealDetailResponse.MerchantDetail(
                        merchant.getUser().getPublicId(),
                        merchant.getStoreName(),
                        merchant.getLogoUrl(),
                        merchant.getAddressLine(),
                        merchant.getWard(),
                        merchant.getDistrict(),
                        merchant.getCity(),
                        true
                )
        );
    }

    private int discountPercent(Deal deal) {
        return deal.getOriginalPrice()
                .subtract(deal.getDiscountedPrice())
                .multiply(BigDecimal.valueOf(100))
                .divide(deal.getOriginalPrice(), 0, RoundingMode.HALF_UP)
                .intValueExact();
    }
}
