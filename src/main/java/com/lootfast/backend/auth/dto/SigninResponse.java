package com.lootfast.backend.auth.dto;

import com.lootfast.backend.user.entity.UserRole;
import com.lootfast.backend.user.entity.AccountType;

import java.util.Set;

public record SigninResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        long refreshExpiresIn,
        String firstName,
        String lastName,
        String email,
        AccountType accountType,
        OnboardingAction nextAction,
        UserRole role,
        Set<UserRole> roles
) {
}
