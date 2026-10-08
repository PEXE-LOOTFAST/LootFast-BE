package com.lootfast.backend.notification.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateNotificationPreferenceRequest(
        @NotNull Boolean matchingDeals,
        @NotNull Boolean buyerOrderUpdates,
        @NotNull Boolean merchantOrderUpdates,
        @NotNull Boolean pickupReminders,
        @NotNull Boolean emailEnabled
) {
}
