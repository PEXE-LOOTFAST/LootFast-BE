package com.lootfast.backend.user.dto;

import com.lootfast.backend.auth.dto.OnboardingAction;
import com.lootfast.backend.user.entity.AccountType;
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
        AccountType accountType,
        OnboardingAction nextAction,
        UserRole role,
        Set<UserRole> roles,
        String displayName,
        String avatarUrl,
        String pickupArea,
        List<String> favoriteCategories,
        Instant createdAt
) {
}
