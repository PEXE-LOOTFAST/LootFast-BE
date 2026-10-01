package com.lootfast.backend.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfirmPickupRequest(
    @NotBlank(message = "Pickup credential is required")
        @Size(max = 128, message = "Pickup credential is too long")
        String credential) {}
