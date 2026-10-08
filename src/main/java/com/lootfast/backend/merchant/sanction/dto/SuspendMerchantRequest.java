package com.lootfast.backend.merchant.sanction.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SuspendMerchantRequest(
    @NotBlank @Size(max = 1000) String reason, @Min(1) @Max(365) int durationDays) {}
