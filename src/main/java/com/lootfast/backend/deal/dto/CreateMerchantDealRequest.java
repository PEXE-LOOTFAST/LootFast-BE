package com.lootfast.backend.deal.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public record CreateMerchantDealRequest(
        @NotBlank @Size(max = 160) String name,
        @NotBlank @Size(max = 100) String categoryCode,
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2)
        BigDecimal originalPrice,
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2)
        BigDecimal discountedPrice,
        @Min(1) @Max(10000) int totalQuantity,
        @Min(0) @Max(1440) int preparationMinutes,
        @NotNull Instant saleStartsAt,
        @NotNull Instant saleEndsAt,
        @NotNull Instant pickupStartsAt,
        @NotNull Instant pickupEndsAt
) {
}
