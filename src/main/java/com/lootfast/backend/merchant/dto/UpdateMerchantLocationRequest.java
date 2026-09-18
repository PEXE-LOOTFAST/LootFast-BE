package com.lootfast.backend.merchant.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateMerchantLocationRequest(
        @NotBlank(message = "Address line is required")
        @Size(max = 255, message = "Address line must not exceed 255 characters")
        String addressLine,

        @NotBlank(message = "Ward is required")
        @Size(max = 100, message = "Ward must not exceed 100 characters")
        String ward,

        @NotBlank(message = "District is required")
        @Size(max = 100, message = "District must not exceed 100 characters")
        String district,

        @NotBlank(message = "City is required")
        @Size(max = 100, message = "City must not exceed 100 characters")
        String city,

        @NotNull(message = "Latitude is required")
        @DecimalMin(value = "-90.000000", message = "Latitude must be at least -90")
        @DecimalMax(value = "90.000000", message = "Latitude must be at most 90")
        @Digits(integer = 2, fraction = 6, message = "Latitude supports at most 6 decimal places")
        BigDecimal latitude,

        @NotNull(message = "Longitude is required")
        @DecimalMin(value = "-180.000000", message = "Longitude must be at least -180")
        @DecimalMax(value = "180.000000", message = "Longitude must be at most 180")
        @Digits(integer = 3, fraction = 6, message = "Longitude supports at most 6 decimal places")
        BigDecimal longitude
) {
}
