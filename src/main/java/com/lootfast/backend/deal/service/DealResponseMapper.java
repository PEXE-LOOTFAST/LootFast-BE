package com.lootfast.backend.deal.service;

import com.lootfast.backend.deal.demo.DemoDeal;
import com.lootfast.backend.deal.dto.DealDetailResponse;
import com.lootfast.backend.deal.dto.DealFeedItemResponse;
import com.lootfast.backend.deal.entity.Deal;
import com.lootfast.backend.deal.entity.DealStatus;
import com.lootfast.backend.merchant.entity.MerchantProfile;
import com.lootfast.backend.review.service.StoreRatingService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class DealResponseMapper {
    private final StoreRatingService reviews;

    public DealResponseMapper(StoreRatingService reviews) {
        this.reviews = reviews;
    }

    public DealFeedItemResponse toFeedItem(Deal deal, BigDecimal distanceKm) {
        MerchantProfile merchant = deal.getMerchant();
        return new DealFeedItemResponse(
                deal.getPublicId(),
                deal.getName(),
                deal.getCategoryCode(),
                deal.getOriginalPrice(),
                deal.getDiscountedPrice(),
                discountPercent(deal.getOriginalPrice(), deal.getDiscountedPrice()),
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
                        merchant.isVerified()
                ),
                false,
                null
        );
    }

    public DealFeedItemResponse toFeedItem(DemoDeal deal, BigDecimal distanceKm) {
        return new DealFeedItemResponse(
                deal.id(),
                deal.name(),
                deal.categoryCode(),
                deal.originalPrice(),
                deal.discountedPrice(),
                discountPercent(deal.originalPrice(), deal.discountedPrice()),
                deal.remainingQuantity(),
                null,
                deal.preparationMinutes(),
                deal.pickupStart(),
                deal.pickupEnd(),
                distanceKm,
                new DealFeedItemResponse.MerchantSummary(
                        deal.storeId(),
                        deal.storeName(),
                        DemoDeal.ADDRESS_LINE,
                        null,
                        null,
                        null,
                        false
                ),
                true,
                DemoDeal.NOTICE
        );
    }

    public DealDetailResponse toDetail(Deal deal, BigDecimal distanceKm) {
        MerchantProfile merchant = deal.getMerchant();
        var rating = reviews.get(merchant.getId());
        return new DealDetailResponse(
                deal.getPublicId(),
                deal.getName(),
                deal.getCategoryCode(),
                deal.getOriginalPrice(),
                deal.getDiscountedPrice(),
                discountPercent(deal.getOriginalPrice(), deal.getDiscountedPrice()),
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
                new DealDetailResponse.RatingSummary(rating.averageRating(), rating.reviewCount()),
                new DealDetailResponse.MerchantDetail(
                        merchant.getUser().getPublicId(),
                        merchant.getStoreName(),
                        merchant.getLogoUrl(),
                        merchant.getAddressLine(),
                        merchant.getWard(),
                        merchant.getDistrict(),
                        merchant.getCity(),
                        merchant.isVerified()
                ),
                false,
                null
        );
    }

    public DealDetailResponse toDetail(DemoDeal deal, BigDecimal distanceKm) {
        return new DealDetailResponse(
                deal.id(),
                deal.name(),
                deal.categoryCode(),
                deal.originalPrice(),
                deal.discountedPrice(),
                discountPercent(deal.originalPrice(), deal.discountedPrice()),
                deal.remainingQuantity(),
                null,
                deal.preparationMinutes(),
                deal.pickupStart(),
                deal.pickupEnd(),
                deal.availableFrom(),
                deal.availableUntil(),
                DealStatus.ACTIVE,
                distanceKm,
                0,
                deal.availableFrom(),
                new DealDetailResponse.RatingSummary(null, 0),
                new DealDetailResponse.MerchantDetail(
                        deal.storeId(),
                        deal.storeName(),
                        null,
                        DemoDeal.ADDRESS_LINE,
                        null,
                        null,
                        null,
                        false
                ),
                true,
                DemoDeal.NOTICE
        );
    }

    private static int discountPercent(BigDecimal originalPrice, BigDecimal discountedPrice) {
        return originalPrice
                .subtract(discountedPrice)
                .multiply(BigDecimal.valueOf(100))
                .divide(originalPrice, 0, RoundingMode.HALF_UP)
                .intValueExact();
    }
}
