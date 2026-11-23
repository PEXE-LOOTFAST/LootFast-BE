package com.lootfast.backend.notification.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class RedisUserEventGatewayTests {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final LocalUserEventDelivery delivery = mock(LocalUserEventDelivery.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RedisUserEventGateway gateway = new RedisUserEventGateway(
            redis, objectMapper, delivery, new RealtimeProperties(null, "test-channel", null, null));

    @Test
    void publishesToRedisAndDeliversRelayedMessagesLocally() throws Exception {
        var envelope = new RealtimeEnvelope(RealtimeEnvelope.UNREAD_COUNT_CHANGED,
                new RealtimeEnvelope.UnreadCount(3));

        gateway.send(42L, envelope);

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(redis).convertAndSend(eq("test-channel"), body.capture());
        verify(delivery, never()).deliver(anyString(), any());

        gateway.onMessage(new DefaultMessage("test-channel".getBytes(StandardCharsets.UTF_8),
                body.getValue().getBytes(StandardCharsets.UTF_8)), null);

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(delivery).deliver(eq("42"), payload.capture());
        JsonNode delivered = (JsonNode) payload.getValue();
        assertEquals("UNREAD_COUNT_CHANGED", delivered.get("event").asText());
        assertEquals(3, delivered.get("data").get("unreadCount").asInt());
    }

    @Test
    void fallsBackToLocalDeliveryWhenRedisIsDown() {
        doThrow(new RedisConnectionFailureException("down")).when(redis).convertAndSend(anyString(), anyString());
        var envelope = new RealtimeEnvelope(RealtimeEnvelope.UNREAD_COUNT_CHANGED,
                new RealtimeEnvelope.UnreadCount(0));

        gateway.send(7L, envelope);

        verify(delivery).deliver("7", envelope);
    }

    @Test
    void ignoresMalformedRelayMessages() {
        gateway.onMessage(new DefaultMessage("c".getBytes(), "{\"envelope\":\"x\"}".getBytes()), null);
        gateway.onMessage(new DefaultMessage("c".getBytes(), "not json".getBytes()), null);

        verify(delivery, never()).deliver(anyString(), any());
    }
}
