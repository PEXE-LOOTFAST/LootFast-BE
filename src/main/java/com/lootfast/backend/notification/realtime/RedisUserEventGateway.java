package com.lootfast.backend.notification.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Multi-instance gateway. Each event is published once to a Redis pub/sub channel; every instance
 * subscribes (see {@link RedisRealtimeConfig}) and delivers it to the sessions it holds locally.
 *
 * <p>Redis pub/sub is fire-and-forget: a message is lost if Redis is down or no instance is
 * subscribed. That is acceptable because notifications are already persisted and clients re-sync
 * over REST. If publishing fails we still deliver locally so a single-instance deployment keeps
 * working while Redis is unavailable.
 */
@Component
@ConditionalOnProperty(name = "lootfast.realtime.relay", havingValue = "redis", matchIfMissing = true)
public class RedisUserEventGateway implements UserEventGateway, MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisUserEventGateway.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final LocalUserEventDelivery delivery;
    private final String channel;

    public RedisUserEventGateway(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            LocalUserEventDelivery delivery,
            RealtimeProperties properties
    ) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.delivery = delivery;
        this.channel = properties.redisChannel();
    }

    @Override
    public void send(Long userId, RealtimeEnvelope envelope) {
        String body;
        try {
            body = objectMapper.writeValueAsString(new RelayMessage(userId.toString(), envelope));
        } catch (Exception exception) {
            log.warn("Could not serialize realtime event {} ({})", envelope.event(),
                    exception.getClass().getSimpleName());
            return;
        }
        try {
            redis.convertAndSend(channel, body);
        } catch (RuntimeException exception) {
            log.warn("Redis realtime relay unavailable, delivering locally only ({})",
                    exception.getClass().getSimpleName());
            delivery.deliver(userId.toString(), envelope);
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            JsonNode root = objectMapper.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
            String userId = root.path("userId").asText(null);
            JsonNode envelope = root.get("envelope");
            if (userId == null || envelope == null || !envelope.isObject()) {
                log.warn("Ignoring malformed realtime relay message");
                return;
            }
            delivery.deliver(userId, envelope);
        } catch (Exception exception) {
            log.warn("Could not deliver relayed realtime event ({})", exception.getClass().getSimpleName());
        }
    }

    record RelayMessage(String userId, RealtimeEnvelope envelope) {
    }
}
