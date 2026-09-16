package com.lootfast.backend.deal.dto;

import java.time.Instant;

public record DealDiscoveryQuery(
        double latitude,
        double longitude,
        double radiusKm,
        String keyword,
        String categoryCode,
        Integer minDiscountPercent,
        Instant pickupFrom,
        Instant pickupTo,
        int page,
        int size
) {
}
