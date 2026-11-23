package com.lootfast.backend.notification.event;

import com.lootfast.backend.notification.dto.NotificationResponse;

/**
 * Published inside the transaction that stored the notification. Realtime delivery listens for
 * it only after commit, so a rolled-back notification is never pushed.
 */
public record NotificationCreatedEvent(Long recipientUserId, NotificationResponse notification) {
}
