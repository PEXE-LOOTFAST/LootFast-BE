package com.lootfast.backend.deal.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record ReuseDealTemplateRequest(
    @Min(1) @Max(10000) int totalQuantity,
    @NotNull Instant saleStartsAt,
    @NotNull Instant saleEndsAt,
    @NotNull Instant pickupStartsAt,
    @NotNull Instant pickupEndsAt) {}
