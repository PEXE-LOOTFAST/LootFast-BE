package com.lootfast.backend.user.dto;

import com.lootfast.backend.user.entity.UserProfile;

import java.util.List;

public record DiscoveryPreferencesResponse(
        String pickupArea,
        List<String> favoriteCategories
) {
    public static DiscoveryPreferencesResponse from(UserProfile profile) {
        return new DiscoveryPreferencesResponse(
                profile.getPickupArea(),
                profile.getFavoriteCategories()
        );
    }
}
