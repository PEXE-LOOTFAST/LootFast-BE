package com.lootfast.backend.buyer.savedfilter.dto;

import jakarta.validation.constraints.*;

public record FilterNotificationRequest(
    @NotNull Boolean enabled, @NotNull @Min(0) Long expectedRevision) {}
