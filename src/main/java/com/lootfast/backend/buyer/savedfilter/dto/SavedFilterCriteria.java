package com.lootfast.backend.buyer.savedfilter.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalTime;

public record SavedFilterCriteria(
    @NotBlank @Size(max = 80) String name,
    @NotNull @DecimalMin("-90") @DecimalMax("90") @Digits(integer = 3, fraction = 6)
        BigDecimal latitude,
    @NotNull @DecimalMin("-180") @DecimalMax("180") @Digits(integer = 3, fraction = 6)
        BigDecimal longitude,
    @NotNull @DecimalMin("0.1") @DecimalMax("50") @Digits(integer = 2, fraction = 2)
        BigDecimal radiusKm,
    @Size(max = 100) String keyword,
    @Size(max = 100) String categoryCode,
    @DecimalMin("0") @DecimalMax("9999999999.99") @Digits(integer = 10, fraction = 2)
        BigDecimal maxPrice,
    @Min(0) @Max(100) Integer minDiscountPercent,
    @Schema(
            type = "string",
            example = "18:00:00",
            description = "Giờ nhận bắt đầu tại Việt Nam, HH:mm:ss")
        LocalTime pickupFrom,
    @Schema(
            type = "string",
            example = "20:00:00",
            description = "Giờ nhận kết thúc tại Việt Nam, HH:mm:ss")
        LocalTime pickupTo) {}
