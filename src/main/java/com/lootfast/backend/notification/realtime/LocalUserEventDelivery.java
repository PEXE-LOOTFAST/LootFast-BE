package com.lootfast.backend.notification.realtime;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/** Delivers to sessions connected to this instance only; a no-op when the user is not here. */
@Component
public class LocalUserEventDelivery {

    private final SimpMessagingTemplate messagingTemplate;

    public LocalUserEventDelivery(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void deliver(String userId, Object payload) {
        messagingTemplate.convertAndSendToUser(userId, WebSocketConfig.USER_NOTIFICATION_QUEUE, payload);
    }
}
