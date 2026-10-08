package com.lootfast.backend.premium.dto;

import java.time.Instant;
import java.util.UUID;

public record PremiumEntitlement(
    UUID id,
    UUID userId,
    String benefit,
    Instant startsAt,
    Instant endsAt,
    Instant grantedAt,
    UUID grantedBy,
    String grantReason,
    Instant revokedAt,
    UUID revokedBy,
    String revokeReason,
    String source,
    UUID subscriptionPeriodId) {}
