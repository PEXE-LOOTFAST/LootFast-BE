package com.lootfast.backend.deal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RemoveDealRequest(
    @NotBlank(message = "Removal reason is required")
        @Size(max = 500, message = "Removal reason is too long")
        String reason) {}
