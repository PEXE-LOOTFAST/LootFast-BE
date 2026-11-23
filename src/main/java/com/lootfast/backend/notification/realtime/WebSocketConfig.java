package com.lootfast.backend.notification.realtime;

import com.lootfast.backend.config.CorsProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

/**
 * Kênh realtime STOMP over WebSocket.
 *
 * <p>Client kết nối tới {@value #ENDPOINT}, gửi frame CONNECT kèm header
 * {@code Authorization: Bearer <access token>}, rồi SUBSCRIBE {@value #USER_NOTIFICATION_QUEUE_CLIENT}.
 * Spring tự định tuyến "/user/..." tới đúng phiên của user, nên không có topic chung nào để
 * user này nghe được dữ liệu của user khác.
 */
@Configuration
@EnableWebSocketMessageBroker
@EnableConfigurationProperties(RealtimeProperties.class)
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    public static final String ENDPOINT = "/ws";
    /** Destination phía server dùng với {@code convertAndSendToUser}. */
    public static final String USER_NOTIFICATION_QUEUE = "/queue/notifications";
    /** Destination client SUBSCRIBE. */
    public static final String USER_NOTIFICATION_QUEUE_CLIENT = "/user/queue/notifications";

    private static final long HEARTBEAT_MILLIS = 10_000;

    private final CorsProperties corsProperties;
    private final StompAuthChannelInterceptor authInterceptor;
    private final WebSocketSessionRegistry sessionRegistry;

    public WebSocketConfig(
            CorsProperties corsProperties,
            StompAuthChannelInterceptor authInterceptor,
            WebSocketSessionRegistry sessionRegistry
    ) {
        this.corsProperties = corsProperties;
        this.authInterceptor = authInterceptor;
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Native WebSocket only (no SockJS). Origin check reuses the explicit CORS allow-list;
        // native mobile clients that send no Origin header are still accepted.
        registry.addEndpoint(ENDPOINT)
                .setAllowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue")
                .setHeartbeatValue(new long[]{HEARTBEAT_MILLIS, HEARTBEAT_MILLIS})
                .setTaskScheduler(realtimeHeartbeatScheduler());
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration
                .setMessageSizeLimit(8 * 1024)
                .setSendBufferSizeLimit(512 * 1024)
                .setSendTimeLimit(15_000)
                .setTimeToFirstMessage(15_000)
                .addDecoratorFactory(sessionRegistry);
    }

    @Bean
    ThreadPoolTaskScheduler realtimeHeartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        return scheduler;
    }
}
