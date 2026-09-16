package com.lootfast.backend.merchant.dto;

import com.lootfast.backend.merchant.entity.MerchantProfile;

import java.time.Instant;

public record MerchantProfileResponse(
        String storeName,
        String description,
        String businessCategory,
        String contactEmail,
        String contactPhone,
        String logoUrl,
        String coverUrl,
        Instant createdAt,
        Instant updatedAt
) {
    public static MerchantProfileResponse from(MerchantProfile profile) {
        return new MerchantProfileResponse(
                profile.getStoreName(),
                profile.getDescription(),
                profile.getBusinessCategory(),
                profile.getContactEmail(),
                profile.getContactPhone(),
                profile.getLogoUrl(),
                profile.getCoverUrl(),
                profile.getCreatedAt(),
                profile.getUpdatedAt()
        );
    }
}
