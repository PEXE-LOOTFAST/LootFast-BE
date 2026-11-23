package com.lootfast.backend.notification.realtime;

import com.lootfast.backend.auth.service.JwtService;
import com.lootfast.backend.notification.dto.NotificationResponse;
import com.lootfast.backend.notification.entity.DealMatchReason;
import com.lootfast.backend.notification.entity.NotificationType;
import com.lootfast.backend.notification.entity.UserNotification;
import com.lootfast.backend.notification.event.NotificationCreatedEvent;
import com.lootfast.backend.notification.repository.UserNotificationRepository;
import com.lootfast.backend.notification.service.UserNotificationService;
import com.lootfast.backend.user.entity.User;
import com.lootfast.backend.user.entity.UserRole;
import com.lootfast.backend.user.entity.UserStatus;
import com.lootfast.backend.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RealtimeNotificationWebSocketTests {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @LocalServerPort private int port;
    @Autowired private UserRepository users;
    @Autowired private JwtService tokens;
    @Autowired private ApplicationEventPublisher events;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private SimpUserRegistry userRegistry;
    @Autowired private UserNotificationRepository notifications;
    @Autowired private UserNotificationService notificationService;

    private final List<StompSession> sessions = new ArrayList<>();

    @AfterEach
    void disconnect() {
        sessions.forEach(session -> {
            if (session.isConnected()) {
                session.disconnect();
            }
        });
    }

    @Test
    void rejectsConnectWithoutValidToken() {
        assertThrows(ExecutionException.class, () -> connectFuture(null).get(5, TimeUnit.SECONDS));
        assertThrows(ExecutionException.class, () -> connectFuture("not-a-jwt").get(5, TimeUnit.SECONDS));
    }

    @Test
    void deliversCommittedNotificationsOnlyToTheirRecipient() throws Exception {
        User alice = user();
        User bob = user();
        BlockingQueue<Map<String, Object>> aliceFrames = subscribe(alice);
        BlockingQueue<Map<String, Object>> bobFrames = subscribe(bob);

        UUID notificationId = UUID.randomUUID();
        publishInTransaction(alice, notificationId, false);

        Map<String, Object> frame = aliceFrames.poll(5, TimeUnit.SECONDS);
        assertNotNull(frame, "alice should receive her notification");
        assertEquals("NOTIFICATION_CREATED", frame.get("event"));
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) frame.get("data");
        assertEquals(notificationId.toString(), data.get("notificationId"));
        assertEquals("MATCHING_DEAL", data.get("type"));
        assertTrue(data.get("createdAt") instanceof String, "timestamps must be ISO strings");
        assertNull(bobFrames.poll(500, TimeUnit.MILLISECONDS), "bob must not see alice's notification");
    }

    @Test
    void rolledBackNotificationsAreNeverPushed() throws Exception {
        User alice = user();
        BlockingQueue<Map<String, Object>> frames = subscribe(alice);

        publishInTransaction(alice, UUID.randomUUID(), true);

        assertNull(frames.poll(1, TimeUnit.SECONDS));
    }

    @Test
    void markAllReadPushesTheNewUnreadCount() throws Exception {
        User alice = user();
        notifications.saveAndFlush(new UserNotification(alice, DealMatchReason.FAVORITE_STORE,
                UUID.randomUUID(), UUID.randomUUID(), "title", "message", null));
        BlockingQueue<Map<String, Object>> frames = subscribe(alice);

        notificationService.markAllRead(alice.getId().toString());

        Map<String, Object> frame = frames.poll(5, TimeUnit.SECONDS);
        assertNotNull(frame);
        assertEquals("UNREAD_COUNT_CHANGED", frame.get("event"));
        assertEquals(Map.of("unreadCount", 0), frame.get("data"));
    }

    @Test
    void subscribingOutsideTheUserQueueClosesTheSession() throws Exception {
        StompSession session = connectFuture(tokens.issueAccessToken(user()).value()).get(5, TimeUnit.SECONDS);
        sessions.add(session);

        session.subscribe("/queue/notifications", new CollectingHandler(new LinkedBlockingQueue<>()));

        long deadline = System.currentTimeMillis() + 5_000;
        while (session.isConnected() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertFalse(session.isConnected());
    }

    private void publishInTransaction(User recipient, UUID notificationId, boolean rollback) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            events.publishEvent(new NotificationCreatedEvent(recipient.getId(), new NotificationResponse(
                    notificationId, NotificationType.MATCHING_DEAL, DealMatchReason.FAVORITE_STORE,
                    UUID.randomUUID(), null, UUID.randomUUID(), "Deal phù hợp", "message", null,
                    false, Instant.now(), null)));
            if (rollback) {
                status.setRollbackOnly();
            }
        });
    }

    private BlockingQueue<Map<String, Object>> subscribe(User user) throws Exception {
        StompSession session = connectFuture(tokens.issueAccessToken(user).value()).get(5, TimeUnit.SECONDS);
        sessions.add(session);
        BlockingQueue<Map<String, Object>> frames = new LinkedBlockingQueue<>();
        session.subscribe(WebSocketConfig.USER_NOTIFICATION_QUEUE_CLIENT, new CollectingHandler(frames));
        awaitSubscription(user.getId().toString());
        return frames;
    }

    /** STOMP has no SUBSCRIBE acknowledgement; wait until the server registered it. */
    private void awaitSubscription(String userId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            var simpUser = userRegistry.getUser(userId);
            if (simpUser != null && simpUser.getSessions().stream()
                    .anyMatch(session -> !session.getSubscriptions().isEmpty())) {
                Thread.sleep(100); // let the broker apply the subscription as well
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("subscription was not registered");
    }

    private java.util.concurrent.CompletableFuture<StompSession> connectFuture(String token) {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new MappingJackson2MessageConverter());
        StompHeaders connectHeaders = new StompHeaders();
        if (token != null) {
            connectHeaders.add("Authorization", "Bearer " + token);
        }
        return client.connectAsync("ws://localhost:" + port + WebSocketConfig.ENDPOINT,
                new WebSocketHttpHeaders(), connectHeaders, new StompSessionHandlerAdapter() {
                });
    }

    private User user() {
        int n = SEQUENCE.incrementAndGet();
        return users.save(new User("Realtime", "Tester", "realtime-%d@example.com".formatted(n),
                "55%07d".formatted(n), "encoded-password", UserRole.BUYER, UserStatus.VERIFIED));
    }

    private record CollectingHandler(BlockingQueue<Map<String, Object>> frames) implements StompFrameHandler {
        @Override
        public Type getPayloadType(StompHeaders headers) {
            return Map.class;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void handleFrame(StompHeaders headers, Object payload) {
            frames.add((Map<String, Object>) payload);
        }
    }
}
