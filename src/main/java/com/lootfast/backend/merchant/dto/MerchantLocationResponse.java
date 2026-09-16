package com.lootfast.backend.merchant.dto;

import com.lootfast.backend.merchant.entity.MerchantProfile;

import java.math.BigDecimal;

public record MerchantLocationResponse(
        String addressLine,
        String ward,
        String district,
        String city,
        BigDecimal latitude,
        BigDecimal longitude
) {
    public static MerchantLocationResponse from(MerchantProfile profile) {
        return new MerchantLocationResponse(
                profile.getAddressLine(),
                profile.getWard(),
                profile.getDistrict(),
                profile.getCity(),
                profile.getLatitude(),
                profile.getLongitude()
        );
    }
}
