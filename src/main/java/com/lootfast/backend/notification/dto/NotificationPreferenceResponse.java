package com.lootfast.backend.notification.dto;

import com.lootfast.backend.notification.entity.NotificationPreference;

public record NotificationPreferenceResponse(
        boolean matchingDeals,
        boolean buyerOrderUpdates,
        boolean merchantOrderUpdates,
        boolean pickupReminders,
        boolean emailEnabled
) {
    public static NotificationPreferenceResponse defaults() {
        return new NotificationPreferenceResponse(true, true, true, true, false);
    }

    public static NotificationPreferenceResponse from(NotificationPreference preference) {
        return new NotificationPreferenceResponse(
                preference.isMatchingDealsEnabled(),
                preference.isBuyerOrderUpdatesEnabled(),
                preference.isMerchantOrderUpdatesEnabled(),
                preference.isPickupRemindersEnabled(),
                preference.isEmailEnabled()
        );
    }
}
