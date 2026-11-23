package com.lootfast.backend.notification.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Theo dõi các WebSocket session của instance hiện tại để đóng những kết nối không còn hợp lệ:
 * <ul>
 *   <li>đã handshake nhưng không gửi CONNECT hợp lệ trong {@code connectTimeout};</li>
 *   <li>access token dùng lúc CONNECT đã hết hạn (client phải refresh token và kết nối lại).</li>
 * </ul>
 * Nhờ vậy tài khoản bị khóa sẽ mất kênh realtime chậm nhất sau khi access token hết hạn.
 *
 * <p>Đây là trạng thái cục bộ của process, nhưng chỉ mô tả socket đang mở trên chính instance
 * này nên không ảnh hưởng tính đúng đắn khi scale ngang.
 */
@Component
public class WebSocketSessionRegistry implements WebSocketHandlerDecoratorFactory {

    /** Close code in the application range (4000-4999) so clients can tell "re-authenticate". */
    static final CloseStatus TOKEN_EXPIRED = new CloseStatus(4401, "Access token expired");
    static final CloseStatus CONNECT_TIMEOUT = new CloseStatus(4408, "STOMP CONNECT timeout");

    private static final Logger log = LoggerFactory.getLogger(WebSocketSessionRegistry.class);

    private final Map<String, TrackedSession> sessions = new ConcurrentHashMap<>();
    private final RealtimeProperties properties;
    private final Clock clock;

    public WebSocketSessionRegistry(RealtimeProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public WebSocketHandler decorate(WebSocketHandler handler) {
        return new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                sessions.put(session.getId(), new TrackedSession(session, clock.instant()));
                super.afterConnectionEstablished(session);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
                sessions.remove(session.getId());
                super.afterConnectionClosed(session, closeStatus);
            }
        };
    }

    @Scheduled(
            fixedDelayString = "${lootfast.realtime.session-sweep-interval:PT30S}",
            initialDelayString = "${lootfast.realtime.session-sweep-interval:PT30S}"
    )
    public void closeStaleSessions() {
        Instant now = clock.instant();
        sessions.values().forEach(tracked -> {
            CloseStatus status = staleStatus(tracked, now);
            if (status != null) {
                close(tracked.session(), status);
            }
        });
    }

    public int activeSessionCount() {
        return sessions.size();
    }

    CloseStatus staleStatus(TrackedSession tracked, Instant now) {
        Object expiresAt = tracked.session().getAttributes().get(StompAuthChannelInterceptor.TOKEN_EXPIRES_AT);
        if (expiresAt instanceof Instant instant) {
            return instant.isAfter(now) ? null : TOKEN_EXPIRED;
        }
        boolean connectOverdue = !tracked.openedAt().plus(properties.connectTimeout()).isAfter(now);
        return connectOverdue ? CONNECT_TIMEOUT : null;
    }

    private void close(WebSocketSession session, CloseStatus status) {
        try {
            if (session.isOpen()) {
                session.close(status);
            }
        } catch (IOException | RuntimeException exception) {
            log.debug("Could not close WebSocket session {} ({})", session.getId(),
                    exception.getClass().getSimpleName());
        } finally {
            sessions.remove(session.getId());
        }
    }

    record TrackedSession(WebSocketSession session, Instant openedAt) {
    }
}
