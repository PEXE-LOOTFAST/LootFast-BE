package com.lootfast.backend.buyer.savedfilter.dto;

import java.time.Instant;
import java.util.UUID;

public record SavedFilterResponse(
    UUID id,
    SavedFilterCriteria criteria,
    long revision,
    Instant createdAt,
    Instant updatedAt,
    boolean notificationsEnabled) {}
