package com.lootfast.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshTokenRequest(
        @NotBlank(message = "Refresh token is required")
        @Size(max = 200, message = "Refresh token must not exceed 200 characters")
        String refreshToken
) {
}
