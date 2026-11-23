package com.lootfast.backend.notification.realtime;

import com.lootfast.backend.security.JwtRoleConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

/**
 * Xác thực và phân quyền cho mọi frame STOMP client gửi lên.
 *
 * <p>Trình duyệt không gửi được header {@code Authorization} trong HTTP handshake của WebSocket,
 * nên handshake được mở công khai và JWT được kiểm tra ở frame CONNECT. Token được giải mã bằng
 * cùng {@link JwtDecoder} với REST API, vì vậy tài khoản bị khóa hoặc đã đổi mật khẩu
 * (auth_version) cũng bị từ chối ở đây.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    static final String TOKEN_EXPIRES_AT = "lootfast.tokenExpiresAt";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String USER_QUEUE_PREFIX = "/user/queue/";
    private static final Logger log = LoggerFactory.getLogger(StompAuthChannelInterceptor.class);

    private final JwtDecoder jwtDecoder;
    private final JwtRoleConverter roleConverter;
    private final Clock clock;

    public StompAuthChannelInterceptor(JwtDecoder jwtDecoder, JwtRoleConverter roleConverter, Clock clock) {
        this.jwtDecoder = jwtDecoder;
        this.roleConverter = roleConverter;
        this.clock = clock;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message; // heartbeat or non-STOMP internal message
        }
        StompCommand command = accessor.getCommand();
        switch (command) {
            case CONNECT, STOMP -> authenticate(accessor);
            case SUBSCRIBE -> authorizeSubscribe(accessor);
            case UNSUBSCRIBE, DISCONNECT -> {
                // always allowed so clients can clean up
            }
            default -> throw new MessageDeliveryException("STOMP command is not allowed: " + command);
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String token = bearerToken(accessor);
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(token);
        } catch (JwtException exception) {
            throw new MessageDeliveryException("Invalid or expired access token");
        }
        var authorities = roleConverter.convert(jwt).getAuthorities();
        // Principal name = user id (JWT subject); user destinations are addressed by this value.
        accessor.setUser(new JwtAuthenticationToken(jwt, authorities, jwt.getSubject()));
        Map<String, Object> attributes = accessor.getSessionAttributes();
        if (attributes != null && jwt.getExpiresAt() != null) {
            attributes.put(TOKEN_EXPIRES_AT, jwt.getExpiresAt());
        }
        log.debug("WebSocket session {} authenticated for user {}", accessor.getSessionId(), jwt.getSubject());
    }

    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        if (accessor.getUser() == null) {
            throw new MessageDeliveryException("Authentication required");
        }
        requireTokenNotExpired(accessor);
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith(USER_QUEUE_PREFIX)) {
            throw new MessageDeliveryException("Subscription destination is not allowed");
        }
    }

    private void requireTokenNotExpired(StompHeaderAccessor accessor) {
        Map<String, Object> attributes = accessor.getSessionAttributes();
        Object expiresAt = attributes == null ? null : attributes.get(TOKEN_EXPIRES_AT);
        if (expiresAt instanceof Instant instant && !instant.isAfter(clock.instant())) {
            throw new MessageDeliveryException("Access token expired");
        }
    }

    private String bearerToken(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null) {
            header = accessor.getFirstNativeHeader("authorization");
        }
        if (header == null || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            throw new MessageDeliveryException("Authentication required");
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            throw new MessageDeliveryException("Authentication required");
        }
        return token;
    }
}
