package com.lootfast.backend.deal.dto;

import com.lootfast.backend.deal.entity.DealTemplate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DealTemplateResponse(
    UUID templateId,
    UUID sourceDealId,
    String name,
    String categoryCode,
    BigDecimal originalPrice,
    BigDecimal discountedPrice,
    int defaultQuantity,
    int preparationMinutes,
    long version,
    Instant createdAt) {
  public static DealTemplateResponse from(DealTemplate template) {
    return new DealTemplateResponse(
        template.getPublicId(),
        template.getSourceDeal() == null ? null : template.getSourceDeal().getPublicId(),
        template.getName(),
        template.getCategoryCode(),
        template.getOriginalPrice(),
        template.getDiscountedPrice(),
        template.getDefaultQuantity(),
        template.getPreparationMinutes(),
        template.getVersion(),
        template.getCreatedAt());
  }
}
