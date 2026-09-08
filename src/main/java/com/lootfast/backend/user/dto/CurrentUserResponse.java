package com.lootfast.backend.user.dto;

import com.lootfast.backend.user.entity.UserRole;

import java.time.Instant;

public record CurrentUserResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        UserRole role,
        Instant createdAt
) {
}
