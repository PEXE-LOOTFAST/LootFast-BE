package com.lootfast.backend.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateDiscoveryPreferencesRequest(
        @Size(max = 255) String pickupArea,
        @NotNull @Size(max = 20) List<@NotBlank @Size(max = 100) String> favoriteCategories
) {
}
