package com.lootfast.backend.premium.subscription.dto;

import com.lootfast.backend.premium.catalog.dto.PlanAudience;
import com.lootfast.backend.premium.dto.PremiumBenefit;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record SubscriptionResponse(
    PlanAudience audience,
    String status,
    boolean eligible,
    Instant paidEndsAt,
    List<Benefit> benefits) {
  public record Benefit(
      PremiumBenefit benefit, boolean enabled, Set<String> activeSources, Instant effectiveUntil) {}

  public record Period(
      UUID id,
      UUID purchaseReference,
      PlanAudience audience,
      UUID planRevisionId,
      int planVersion,
      String planCode,
      String planName,
      long priceVnd,
      String currency,
      int durationDays,
      Set<PremiumBenefit> benefits,
      UUID termsDocumentId,
      String termsVersion,
      Instant confirmedAt,
      Instant startsAt,
      Instant endsAt,
      Instant createdAt) {}

  public record Coverage(PremiumBenefit benefit, Set<String> sources, Instant until) {}
}
