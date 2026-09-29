package com.lootfast.backend.order;

import static org.junit.jupiter.api.Assertions.*;

import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.deal.entity.*;
import com.lootfast.backend.deal.repository.DealRepository;
import com.lootfast.backend.deal.dto.UpdateDealQuantityRequest;
import com.lootfast.backend.deal.service.MerchantDealService;
import com.lootfast.backend.merchant.application.entity.MerchantApplication;
import com.lootfast.backend.merchant.application.repository.MerchantApplicationRepository;
import com.lootfast.backend.merchant.entity.MerchantProfile;
import com.lootfast.backend.merchant.repository.MerchantProfileRepository;
import com.lootfast.backend.order.dto.CreateOrderRequest;
import com.lootfast.backend.order.entity.PaymentMethod;
import com.lootfast.backend.order.repository.OrderRepository;
import com.lootfast.backend.order.service.OrderService;
import com.lootfast.backend.user.entity.*;
import com.lootfast.backend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
    properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true"})
@Testcontainers
class OrderReservationPostgres18ConcurrencyTests {
  @Container
  static final GenericContainer<?> POSTGRES =
      new GenericContainer<>(DockerImageName.parse("postgres:18.6-alpine"))
          .withEnv("POSTGRES_DB", "lootfast_order_test")
          .withEnv("POSTGRES_USER", "lootfast")
          .withEnv("POSTGRES_PASSWORD", "test-password")
          .withExposedPorts(5432)
          .waitingFor(
              Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2));

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.datasource.url",
        () ->
            "jdbc:postgresql://%s:%d/lootfast_order_test"
                .formatted(POSTGRES.getHost(), POSTGRES.getMappedPort(5432)));
    registry.add("spring.datasource.username", () -> "lootfast");
    registry.add("spring.datasource.password", () -> "test-password");
  }

  @Autowired UserRepository users;
  @Autowired MerchantProfileRepository merchants;
  @Autowired DealRepository deals;
  @Autowired OrderRepository orders;
  @Autowired OrderService service;
  @Autowired MerchantApplicationRepository applications;
  @Autowired MerchantDealService merchantDealService;

  // Tests share one container without a transaction and each builds its own merchant/deal/buyer, so
  // only the orders table needs clearing: it is the one thing asserted globally (orders.count()),
  // and nothing references it by foreign key, so wiping it is safe and self-contained.
  @BeforeEach
  void clean() {
    orders.deleteAll();
  }

  @Test
  void concurrentBuyersCannotReserveTheSameLastPortion() throws Exception {
    User merchantUser =
        users.saveAndFlush(
            new User(
                "Merchant",
                "One",
                UUID.randomUUID() + "@test.local",
                null,
                null,
                UserRole.MERCHANT,
                UserStatus.VERIFIED));
    MerchantProfile merchant = new MerchantProfile(merchantUser);
    merchant.updateStoreInformation("Concurrency store", "description", "MEAL", null, null);
    merchant.updateLocation("1 Test", "Ward", "District", "City", BigDecimal.TEN, BigDecimal.TEN);
    merchants.saveAndFlush(merchant);
    Instant now = Instant.now();
    Deal deal =
        deals.saveAndFlush(
            new Deal(
                merchant,
                "Last meal",
                "MEAL",
                BigDecimal.valueOf(100_000),
                BigDecimal.valueOf(50_000),
                1,
                "https://example.test/image.jpg",
                10,
                now.plusSeconds(3600),
                now.plusSeconds(7200),
                now.minusSeconds(60),
                now.plusSeconds(7200),
                DealStatus.ACTIVE));
    User first = buyer();
    User second = buyer();
    var pool = Executors.newFixedThreadPool(2);
    var gate = new CountDownLatch(1);
    try {
      Future<String> a = pool.submit(() -> reserve(gate, first, deal));
      Future<String> b = pool.submit(() -> reserve(gate, second, deal));
      gate.countDown();
      List<String> outcomes = List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
      assertEquals(1, outcomes.stream().filter("SUCCESS"::equals).count());
      assertEquals(
          1,
          outcomes.stream()
              .filter(
                  value ->
                      value.equals("DEAL_NOT_RESERVABLE") || value.equals("INSUFFICIENT_STOCK"))
              .count());
      assertEquals(1, orders.count());
      assertEquals(
          0, deals.findByPublicId(deal.getPublicId()).orElseThrow().getRemainingQuantity());
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void stockUpdateAndReservationCannotCreateNegativeInventory() throws Exception {
    User merchantUser =
        users.saveAndFlush(
            new User(
                "Merchant",
                "Stock",
                UUID.randomUUID() + "@test.local",
                null,
                null,
                UserRole.MERCHANT,
                UserStatus.VERIFIED));
    User admin =
        users.saveAndFlush(
            new User(
                "Admin",
                "Stock",
                UUID.randomUUID() + "@test.local",
                null,
                null,
                UserRole.ADMIN,
                UserStatus.VERIFIED));
    MerchantProfile merchant = new MerchantProfile(merchantUser);
    merchant.updateStoreInformation("Stock store", "description", "MEAL", null, null);
    merchant.updateLocation("1 Test", "Ward", "District", "City", BigDecimal.TEN, BigDecimal.TEN);
    merchants.saveAndFlush(merchant);
    MerchantApplication application =
        new MerchantApplication(merchantUser, "03123456789012", UUID.randomUUID());
    application.markPending("licenses/stock", "PNG", Instant.now().minusSeconds(60));
    application.approve(admin, Instant.now());
    applications.saveAndFlush(application);

    Instant now = Instant.now();
    Deal deal =
        deals.saveAndFlush(
            new Deal(
                merchant,
                "Concurrent stock",
                "MEAL",
                BigDecimal.valueOf(100_000),
                BigDecimal.valueOf(50_000),
                1,
                "https://example.test/image.jpg",
                10,
                now.plusSeconds(3600),
                now.plusSeconds(7200),
                now.minusSeconds(60),
                now.plusSeconds(7200),
                DealStatus.ACTIVE));
    User buyer = buyer();
    var pool = Executors.newFixedThreadPool(2);
    var gate = new CountDownLatch(1);
    try {
      Future<String> reservation = pool.submit(() -> reserve(gate, buyer, deal));
      Future<String> stockUpdate =
          pool.submit(
              () -> {
                gate.await();
                try {
                  merchantDealService.updateQuantity(
                      merchantUser.getId().toString(),
                      deal.getPublicId(),
                      new UpdateDealQuantityRequest(0, deal.getVersion()));
                  return "SUCCESS";
                } catch (ApiException exception) {
                  return exception.getErrorCode().name();
                }
              });
      gate.countDown();
      String reservationOutcome = reservation.get(10, TimeUnit.SECONDS);
      String updateOutcome = stockUpdate.get(10, TimeUnit.SECONDS);

      assertTrue(
          (reservationOutcome.equals("SUCCESS")
                  && (updateOutcome.equals("DEAL_QUANTITY_CONFLICT")
                      || updateOutcome.equals("DEAL_VERSION_CONFLICT")))
              || (updateOutcome.equals("SUCCESS")
                  && (reservationOutcome.equals("DEAL_NOT_RESERVABLE")
                      || reservationOutcome.equals("INSUFFICIENT_STOCK"))));
      Deal reloaded = deals.findByPublicId(deal.getPublicId()).orElseThrow();
      assertTrue(reloaded.getRemainingQuantity() >= 0);
      assertTrue(reloaded.getTotalQuantity() >= reloaded.getRemainingQuantity());
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void concurrentCancelRestoresHeldStockExactlyOnce() throws Exception {
    User merchantUser =
        users.saveAndFlush(
            new User(
                "Merchant",
                "Cancel",
                UUID.randomUUID() + "@test.local",
                null,
                null,
                UserRole.MERCHANT,
                UserStatus.VERIFIED));
    MerchantProfile merchant = new MerchantProfile(merchantUser);
    merchant.updateStoreInformation("Cancel store", "description", "MEAL", null, null);
    merchant.updateLocation("1 Test", "Ward", "District", "City", BigDecimal.TEN, BigDecimal.TEN);
    merchants.saveAndFlush(merchant);
    Instant now = Instant.now();
    Deal deal =
        deals.saveAndFlush(
            new Deal(
                merchant,
                "Cancel meal",
                "MEAL",
                BigDecimal.valueOf(100_000),
                BigDecimal.valueOf(50_000),
                3,
                "https://example.test/image.jpg",
                10,
                now.plusSeconds(3600),
                now.plusSeconds(7200),
                now.minusSeconds(60),
                now.plusSeconds(7200),
                DealStatus.ACTIVE));
    User buyer = buyer();
    UUID orderId =
        service
            .reserve(
                buyer.getId().toString(),
                UUID.randomUUID(),
                new CreateOrderRequest(deal.getPublicId(), 1, PaymentMethod.CASH))
            .orderId();
    assertEquals(2, deals.findByPublicId(deal.getPublicId()).orElseThrow().getRemainingQuantity());

    var pool = Executors.newFixedThreadPool(2);
    var gate = new CountDownLatch(1);
    try {
      Future<String> a = pool.submit(() -> cancel(gate, buyer, orderId));
      Future<String> b = pool.submit(() -> cancel(gate, buyer, orderId));
      gate.countDown();
      List<String> outcomes = List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
      assertEquals(1, outcomes.stream().filter("SUCCESS"::equals).count());
      assertEquals(1, outcomes.stream().filter("ORDER_NOT_CANCELLABLE"::equals).count());
      assertEquals(
          3, deals.findByPublicId(deal.getPublicId()).orElseThrow().getRemainingQuantity());
    } finally {
      pool.shutdownNow();
    }
  }

  private String cancel(CountDownLatch gate, User buyer, UUID orderId) throws Exception {
    gate.await();
    try {
      service.cancel(buyer.getId().toString(), orderId);
      return "SUCCESS";
    } catch (ApiException e) {
      return e.getErrorCode().name();
    }
  }

  private User buyer() {
    return users.saveAndFlush(
        new User(
            "Buyer",
            "One",
            UUID.randomUUID() + "@test.local",
            null,
            null,
            UserRole.BUYER,
            UserStatus.VERIFIED));
  }

  private String reserve(CountDownLatch gate, User buyer, Deal deal) throws Exception {
    gate.await();
    try {
      service.reserve(
          buyer.getId().toString(),
          UUID.randomUUID(),
          new CreateOrderRequest(deal.getPublicId(), 1, PaymentMethod.CASH));
      return "SUCCESS";
    } catch (ApiException e) {
      return e.getErrorCode().name();
    }
  }
}
