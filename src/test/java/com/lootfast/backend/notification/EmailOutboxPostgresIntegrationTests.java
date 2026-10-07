package com.lootfast.backend.notification;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.lootfast.backend.common.email.*;
import com.lootfast.backend.deal.entity.*;
import com.lootfast.backend.deal.repository.DealRepository;
import com.lootfast.backend.merchant.entity.MerchantProfile;
import com.lootfast.backend.merchant.repository.MerchantProfileRepository;
import com.lootfast.backend.notification.entity.*;
import com.lootfast.backend.notification.outbox.*;
import com.lootfast.backend.notification.repository.*;
import com.lootfast.backend.user.entity.*;
import com.lootfast.backend.user.repository.UserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest(
    properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate"})
@Testcontainers
class EmailOutboxPostgresIntegrationTests {
  @Container
  static final GenericContainer<?> POSTGRES =
      new GenericContainer<>("postgres:17-alpine")
          .withEnv("POSTGRES_DB", "lootfast_outbox_test")
          .withEnv("POSTGRES_USER", "lootfast")
          .withEnv("POSTGRES_PASSWORD", "test-password")
          .withExposedPorts(5432)
          .waitingFor(
              Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2));

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry properties) {
    properties.add(
        "spring.datasource.url",
        () ->
            "jdbc:postgresql://%s:%d/lootfast_outbox_test"
                .formatted(POSTGRES.getHost(), POSTGRES.getMappedPort(5432)));
    properties.add("spring.datasource.username", () -> "lootfast");
    properties.add("spring.datasource.password", () -> "test-password");
  }

  static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
  @Autowired EmailOutboxService service;
  @Autowired EmailOutboxStore store;
  @Autowired EmailOutboxRepository outbox;
  @Autowired UserRepository users;
  @Autowired MerchantProfileRepository merchants;
  @Autowired DealRepository deals;
  @Autowired UserNotificationRepository notifications;
  @Autowired NotificationPreferenceRepository preferences;
  @Autowired PlatformTransactionManager transactions;
  @Autowired JdbcTemplate jdbc;
  @MockitoBean Clock clock;
  QueuedEmailSender sender;
  Long recipientId;
  UUID notificationId;
  TransactionTemplate tx;

  @BeforeEach
  void setup() {
    sender = mock(QueuedEmailSender.class);
    when(clock.instant()).thenReturn(NOW);
    tx = new TransactionTemplate(transactions);
    jdbc.update("delete from email_outbox");
    tx.executeWithoutResult(
        status -> {
          User buyer = users.saveAndFlush(user(UserRole.BUYER));
          recipientId = buyer.getId();
          var preference = new NotificationPreference(buyer);
          preference.update(true, true, true, true, true);
          preferences.saveAndFlush(preference);
          User owner = users.saveAndFlush(user(UserRole.MERCHANT));
          var merchant = new MerchantProfile(owner);
          merchant.updateStoreInformation("Outbox Store", null, "MEAL", null, null);
          merchants.saveAndFlush(merchant);
          var deal =
              deals.saveAndFlush(
                  new Deal(
                      merchant,
                      "Meal",
                      "MEAL",
                      BigDecimal.valueOf(100000),
                      BigDecimal.valueOf(40000),
                      5,
                      "https://example.test/image.jpg",
                      10,
                      NOW.plusSeconds(3600),
                      NOW.plusSeconds(7200),
                      NOW.minusSeconds(60),
                      NOW.plusSeconds(1800),
                      DealStatus.ACTIVE));
          var notification =
              notifications.saveAndFlush(
                  new UserNotification(
                      buyer,
                      DealMatchReason.BIG_DISCOUNT,
                      deal.getPublicId(),
                      owner.getPublicId(),
                      "Deal",
                      "Meal deal",
                      null));
          notificationId = notification.getPublicId();
        });
  }

  User user(UserRole role) {
    return new User(
        "Outbox",
        "Tester",
        UUID.randomUUID() + "@example.test",
        null,
        null,
        role,
        UserStatus.VERIFIED);
  }

  void enqueue() {
    tx.executeWithoutResult(
        status ->
            service.enqueue(
                notificationId,
                "recipient@example.test",
                "Deal",
                "<p>Meal</p>",
                EmailPreferenceCategory.MATCHING_DEALS));
  }

  String status() {
    return jdbc.queryForObject(
        "select status from email_outbox where id=?", String.class, notificationId);
  }

  @Test
  void enqueueIsAtomicAndDuplicateNotificationIsIgnored() {
    tx.executeWithoutResult(
        status -> {
          service.enqueue(
              notificationId,
              "recipient@example.test",
              "Deal",
              "<p>Meal</p>",
              EmailPreferenceCategory.MATCHING_DEALS);
          status.setRollbackOnly();
        });
    assertEquals(0, outbox.count());
    enqueue();
    enqueue();
    assertEquals(1, outbox.count());
    assertEquals("PENDING", status());
  }

  @Test
  void concurrentWorkersClaimOneJobOnly() throws Exception {
    enqueue();
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var a =
          executor.submit(
              () -> {
                start.await();
                return store.claim(Duration.ofSeconds(60));
              });
      var b =
          executor.submit(
              () -> {
                start.await();
                return store.claim(Duration.ofSeconds(60));
              });
      start.countDown();
      assertEquals(
          1,
          (a.get(10, TimeUnit.SECONDS).isPresent() ? 1 : 0)
              + (b.get(10, TimeUnit.SECONDS).isPresent() ? 1 : 0));
    }
    assertEquals("PROCESSING", status());
  }

  @Test
  void expiredLeaseIsRecoveredAndOldWorkerCannotFinalize() {
    enqueue();
    var old = store.claim(Duration.ofSeconds(60)).orElseThrow();
    when(clock.instant()).thenReturn(NOW.plusSeconds(61));
    var recovered = store.claim(Duration.ofSeconds(60)).orElseThrow();
    assertEquals(old.id(), recovered.id());
    assertNotEquals(old.token(), recovered.token());
    assertFalse(store.finish(old, EmailDeliveryResult.accepted()));
    assertTrue(store.finish(recovered, EmailDeliveryResult.accepted()));
    assertEquals("SENT", status());
  }

  @Test
  void retryWaitsUntilDueAndKeepsSameProviderKey() {
    enqueue();
    var first = store.claim(Duration.ofSeconds(60)).orElseThrow();
    assertTrue(store.finish(first, EmailDeliveryResult.retry("HTTP_429", Duration.ofSeconds(90))));
    assertEquals("PENDING", status());
    when(clock.instant()).thenReturn(NOW.plusSeconds(89));
    assertTrue(store.claim(Duration.ofSeconds(60)).isEmpty());
    when(clock.instant()).thenReturn(NOW.plusSeconds(90));
    var second = store.claim(Duration.ofSeconds(60)).orElseThrow();
    assertEquals(first.id(), second.id());
    assertEquals(2, second.attempt());
  }

  @Test
  void permanentFailureIsNotRetried() {
    enqueue();
    var job = store.claim(Duration.ofSeconds(60)).orElseThrow();
    store.finish(job, EmailDeliveryResult.failed("HTTP_400"));
    assertEquals("FAILED", status());
    assertTrue(store.claim(Duration.ofSeconds(60)).isEmpty());
  }

  @Test
  void crashesEventuallyExhaustAttempts() {
    enqueue();
    for (int i = 0; i < 5; i++) {
      when(clock.instant()).thenReturn(NOW.plusSeconds(i * 61L));
      assertEquals(i + 1, store.claim(Duration.ofSeconds(60)).orElseThrow().attempt());
    }
    when(clock.instant()).thenReturn(NOW.plusSeconds(5 * 61L));
    assertTrue(store.claim(Duration.ofSeconds(60)).isEmpty());
    assertEquals("FAILED", status());
  }

  @Test
  void preferencesAreRecheckedBeforeDelivery() {
    enqueue();
    tx.executeWithoutResult(
        status -> {
          var p = preferences.findByUserId(recipientId).orElseThrow();
          p.update(false, true, true, true, true);
        });
    assertTrue(store.claim(Duration.ofSeconds(60)).isEmpty());
    assertEquals("CANCELLED", status());
  }

  @Test
  void workerSendsOutsideDatabaseTransactionAndRecordsAcceptance() {
    enqueue();
    var job = store.claim(Duration.ofSeconds(60)).orElseThrow();
    when(sender.sendQueuedEmail(anyString(), any(), anyString(), anyString()))
        .thenAnswer(
            call -> {
              assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
              assertEquals(notificationId, call.getArgument(1));
              return EmailDeliveryResult.accepted();
            });
    var properties =
        new BrevoEmailProperties(
            "https://api.brevo.com/v3",
            "test-key",
            "from@example.test",
            "LootFast",
            Duration.ofSeconds(3),
            Duration.ofSeconds(10));
    new EmailOutboxWorker(store, sender, properties, new SimpleMeterRegistry()).deliver(job);
    assertEquals("SENT", status());
    verify(sender).sendQueuedEmail("recipient@example.test", notificationId, "Deal", "<p>Meal</p>");
  }

  @Test
  void retryableFailuresStopAtAttemptLimit() {
    enqueue();
    for (int i = 0; i < 5; i++) {
      when(clock.instant()).thenReturn(NOW.plusSeconds(i * 10000L));
      var job = store.claim(Duration.ofSeconds(60)).orElseThrow();
      assertTrue(store.finish(job, EmailDeliveryResult.retry("HTTP_503", Duration.ZERO)));
    }
    assertEquals("FAILED", status());
    assertTrue(store.claim(Duration.ofSeconds(60)).isEmpty());
  }

  @Test
  void skipLockedDoesNotWaitForAnotherWorkerTransaction() throws Exception {
    enqueue();
    tx.executeWithoutResult(
        status -> {
          jdbc.queryForObject(
              "select id from email_outbox where id=? for update", UUID.class, notificationId);
          try (var executor = Executors.newSingleThreadExecutor()) {
            try {
              assertTrue(
                  executor
                      .submit(() -> store.claim(Duration.ofSeconds(60)))
                      .get(3, TimeUnit.SECONDS)
                      .isEmpty());
            } catch (Exception exception) {
              throw new AssertionError(exception);
            }
          }
        });
    assertEquals("PENDING", status());
    assertTrue(store.claim(Duration.ofSeconds(60)).isPresent());
  }

  @Test
  void deletingNotificationAlsoDeletesQueuedPersonalData() {
    enqueue();
    tx.executeWithoutResult(
        status -> notifications.delete(notifications.findByPublicId(notificationId).orElseThrow()));
    assertEquals(0, outbox.count());
  }
}
