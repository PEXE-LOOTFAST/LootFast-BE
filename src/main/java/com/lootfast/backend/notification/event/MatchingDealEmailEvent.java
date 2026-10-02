package com.lootfast.backend.notification.event;

import java.math.BigDecimal;
import java.util.UUID;

public record MatchingDealEmailEvent(
        String recipientEmail,
        UUID notificationId,
        String dealName,
        String storeName,
        BigDecimal originalPrice,
        BigDecimal discountedPrice
) {
}
