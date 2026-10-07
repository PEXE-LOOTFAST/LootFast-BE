package com.lootfast.backend.premium.catalog.dto;

import java.time.Instant;
import java.util.UUID;

public record PlanAuditResponse(
    UUID id,
    UUID planRevisionId,
    UUID actorId,
    String action,
    String reason,
    PlanResponse snapshot,
    Instant createdAt) {}
