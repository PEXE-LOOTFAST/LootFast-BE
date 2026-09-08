package com.lootfast.backend.auth.dto;

import com.lootfast.backend.user.entity.UserRole;

public record SigninResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        Long userId,
        String firstName,
        String lastName,
        String email,
        UserRole role
) {
}
