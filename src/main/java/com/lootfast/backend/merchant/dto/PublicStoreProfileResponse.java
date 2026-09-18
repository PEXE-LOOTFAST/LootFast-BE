package com.lootfast.backend.merchant.dto;

import com.lootfast.backend.common.response.PagedResponse;
import com.lootfast.backend.deal.dto.DealFeedItemResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PublicStoreProfileResponse(
        UUID merchantId,
        String storeName,
        String description,
        String businessCategory,
        String contactEmail,
        String contactPhone,
        String logoUrl,
        String coverUrl,
        Address address,
        boolean verified,
        RatingSummary rating,
        List<OpeningHourSlotResponse> openingHours,
        PagedResponse<DealFeedItemResponse> activeDeals
) {
    public record Address(
            String addressLine,
            String ward,
            String district,
            String city,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
    }

    public record RatingSummary(BigDecimal averageRating, long reviewCount) {
    }
}
