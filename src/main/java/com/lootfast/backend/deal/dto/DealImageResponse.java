package com.lootfast.backend.deal.dto;

import com.lootfast.backend.deal.entity.DealImage;

import java.time.Instant;
import java.util.UUID;

public record DealImageResponse(
        UUID imageId,
        String imageUrl,
        int displayOrder,
        Instant createdAt
) {
    public static DealImageResponse from(DealImage image) {
        return new DealImageResponse(
                image.getPublicId(),
                image.getImageUrl(),
                image.getDisplayOrder(),
                image.getCreatedAt()
        );
    }
}
