package com.lootfast.backend.notification.dto;

import com.lootfast.backend.notification.entity.DealMatchReason;
import com.lootfast.backend.notification.entity.NotificationType;
import com.lootfast.backend.notification.entity.UserNotification;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID notificationId,
        NotificationType type,
        DealMatchReason matchReason,
        UUID dealId,
        UUID storeId,
        String title,
        String message,
        String imageUrl,
        boolean read,
        Instant createdAt,
        Instant readAt
) {
    public static NotificationResponse from(UserNotification notification) {
        return new NotificationResponse(
                notification.getPublicId(),
                notification.getType(),
                notification.getMatchReason(),
                notification.getDealPublicId(),
                notification.getStorePublicId(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getImageUrl(),
                notification.getReadAt() != null,
                notification.getCreatedAt(),
                notification.getReadAt()
        );
    }
}
