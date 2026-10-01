package com.lootfast.backend.notification.event;
import java.util.UUID;
public record OrderEmailEvent(String email, UUID notificationId, String title, String message) {}
