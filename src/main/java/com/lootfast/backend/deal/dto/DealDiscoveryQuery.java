package com.lootfast.backend.deal.dto;

import java.time.Instant;

public record DealDiscoveryQuery(
        Double latitude,
        Double longitude,
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
