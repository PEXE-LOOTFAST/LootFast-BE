package com.lootfast.backend.deal.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateDealQuantityRequest(
        @NotNull @Min(0) @Max(10000) Integer totalQuantity,
        @NotNull @Min(0) Long expectedVersion
) {
}
