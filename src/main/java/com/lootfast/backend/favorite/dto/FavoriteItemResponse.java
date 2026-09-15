package com.lootfast.backend.favorite.dto;

import com.lootfast.backend.deal.dto.DealDetailResponse;
import com.lootfast.backend.favorite.entity.FavoriteType;

import java.time.Instant;
import java.util.UUID;

public record FavoriteItemResponse(
        FavoriteType type,
        Instant savedAt,
        DealDetailResponse deal,
        StoreSummary store
) {
    public record StoreSummary(
            UUID merchantId,
            String storeName,
            String logoUrl,
            String addressLine,
            String ward,
            String district,
            String city,
            boolean verified
    ) {
    }
}
