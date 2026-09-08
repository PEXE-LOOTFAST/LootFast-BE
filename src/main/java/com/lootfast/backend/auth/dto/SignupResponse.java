package com.lootfast.backend.auth.dto;

import com.lootfast.backend.user.entity.UserRole;

import java.time.Instant;

public record SignupResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        UserRole role,
        Instant createdAt
) {
}
