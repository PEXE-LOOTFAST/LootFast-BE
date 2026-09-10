package com.lootfast.backend.auth.dto;

import com.lootfast.backend.user.entity.UserRole;

public record SigninResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        long refreshExpiresIn,
        String firstName,
        String lastName,
        String email,
        UserRole role
) {
}
