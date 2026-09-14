package com.lootfast.backend.user.dto;

import com.lootfast.backend.user.entity.UserRole;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record CurrentUserResponse(
        UUID publicId,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        UserRole role,
        Set<UserRole> roles,
        String displayName,
        String avatarUrl,
        String pickupArea,
        List<String> favoriteCategories,
        Instant createdAt
) {
}
