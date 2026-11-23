package com.lootfast.backend.notification.realtime;

/**
 * Sends a realtime event to every open WebSocket session of a user, on whichever instance the
 * session is connected. Delivery is best-effort: the database stays the source of truth and
 * clients re-sync over REST after (re)connecting.
 */
public interface UserEventGateway {

    void send(Long userId, RealtimeEnvelope envelope);
}
