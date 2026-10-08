package com.lootfast.backend.merchant.sanction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WarnMerchantRequest(@NotBlank @Size(max = 1000) String reason) {}
