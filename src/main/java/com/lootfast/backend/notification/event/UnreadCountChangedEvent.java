package com.lootfast.backend.notification.event;

/** Unread badge changed (mark read / mark all read); keeps the user's other devices in sync. */
public record UnreadCountChangedEvent(Long recipientUserId, long unreadCount) {
}
