package com.lootfast.backend.notification.realtime;

/**
 * Body of every frame sent on {@code /user/queue/notifications}:
 * <pre>{"event":"NOTIFICATION_CREATED","data":{...NotificationResponse...}}
 * {"event":"UNREAD_COUNT_CHANGED","data":{"unreadCount":3}}</pre>
 */
public record RealtimeEnvelope(String event, Object data) {

    public static final String NOTIFICATION_CREATED = "NOTIFICATION_CREATED";
    public static final String UNREAD_COUNT_CHANGED = "UNREAD_COUNT_CHANGED";

    public record UnreadCount(long unreadCount) {
    }
}
