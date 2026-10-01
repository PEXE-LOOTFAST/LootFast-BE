package com.lootfast.backend.notification;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.transaction.TestTransaction;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.time.Instant;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true"})
@Testcontainers(disabledWithoutDocker = true)
class OrderLifecycleNotificationPostgresIntegrationTests extends OrderLifecycleNotificationIntegrationTests {
    @Container
    static final GenericContainer<?> POSTGRES = new GenericContainer<>("postgres:17-alpine")
            .withEnv("POSTGRES_DB", "lootfast_test")
            .withEnv("POSTGRES_USER", "lootfast")
            .withEnv("POSTGRES_PASSWORD", "test-password")
            .withExposedPorts(5432)
            .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2));
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://%s:%d/lootfast_test".formatted(POSTGRES.getHost(), POSTGRES.getMappedPort(5432)));
        registry.add("spring.datasource.username", () -> "lootfast");
        registry.add("spring.datasource.password", () -> "test-password");
    }

    @Test
    void concurrentReminderWorkersCreateExactlyOneNotification() throws Exception {
        // Base fixture is stored in the test transaction. Commit it so both workers can see it.
        var now = Instant.parse("2030-01-01T00:00:00Z");
        var deal = deals.saveAndFlush(new com.lootfast.backend.deal.entity.Deal(
                merchant, "Concurrency meal", "MEAL", java.math.BigDecimal.valueOf(100000),
                java.math.BigDecimal.valueOf(50000), 5, "https://example.test/image.jpg", 10,
                now.plusSeconds(3600), now.plusSeconds(7200), now.minusSeconds(60),
                now.plusSeconds(7200), com.lootfast.backend.deal.entity.DealStatus.ACTIVE));
        var response = service.reserve(buyer.getId().toString(), java.util.UUID.randomUUID(),
                new com.lootfast.backend.order.dto.CreateOrderRequest(deal.getPublicId(), 1,
                        com.lootfast.backend.order.entity.PaymentMethod.CASH));
        TestTransaction.flagForCommit();
        TestTransaction.end();
        when(clock.instant()).thenReturn(now.plusSeconds(1800));
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); reminders.remind(response.orderId()); return null; });
            var second = executor.submit(() -> { start.await(); reminders.remind(response.orderId()); return null; });
            start.countDown();
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
        }
        TestTransaction.start();
        assertEquals(1, notifications.findAll().stream().filter(n ->
                response.orderId().equals(n.getOrderPublicId()) && n.getType() ==
                com.lootfast.backend.notification.entity.NotificationType.PICKUP_REMINDER).count());
        // Delete only committed test fixtures; leave each inherited test with an empty notification table.
        notifications.deleteAllInBatch();
        orders.deleteAllInBatch();
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();
    }
}
