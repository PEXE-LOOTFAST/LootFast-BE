package com.lootfast.backend.premium.catalog.dto;

import com.lootfast.backend.premium.dto.PremiumBenefit;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record PlanResponse(
    UUID id,
    String code,
    PlanAudience audience,
    int version,
    long revision,
    String status,
    String name,
    String description,
    long priceVnd,
    String currency,
    int durationDays,
    Set<PremiumBenefit> benefits,
    UUID termsDocumentId,
    String termsVersion,
    Instant publishedAt,
    Instant updatedAt) {}
