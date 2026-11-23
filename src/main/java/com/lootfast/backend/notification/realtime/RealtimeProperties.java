package com.lootfast.backend.notification.realtime;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Cấu hình kênh realtime (WebSocket/STOMP).
 *
 * @param relay                  REDIS: phát qua Redis pub/sub để mọi instance đều giao được tới
 *                               client đang kết nối với nó; LOCAL: chỉ giao trong process hiện tại
 *                               (đủ cho 1 instance và cho test).
 * @param redisChannel           tên kênh Redis pub/sub dùng chung giữa các instance.
 * @param connectTimeout         socket đã handshake nhưng chưa gửi STOMP CONNECT hợp lệ trong
 *                               khoảng này sẽ bị đóng, tránh giữ kết nối rác.
 * @param sessionSweepInterval   chu kỳ quét để đóng socket có access token đã hết hạn.
 */
@ConfigurationProperties(prefix = "lootfast.realtime")
public record RealtimeProperties(
        Relay relay,
        String redisChannel,
        Duration connectTimeout,
        Duration sessionSweepInterval
) {

    public enum Relay { REDIS, LOCAL }

    public RealtimeProperties {
        relay = relay == null ? Relay.REDIS : relay;
        redisChannel = redisChannel == null || redisChannel.isBlank()
                ? "lootfast:realtime:user-events"
                : redisChannel;
        connectTimeout = positiveOr(connectTimeout, Duration.ofSeconds(30));
        sessionSweepInterval = positiveOr(sessionSweepInterval, Duration.ofSeconds(30));
    }

    private static Duration positiveOr(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }
}
