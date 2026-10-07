package com.lootfast.backend.merchant.performance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.lootfast.backend.common.exception.*;
import com.lootfast.backend.merchant.performance.repository.AdvancedPerformanceRepository;
import com.lootfast.backend.merchant.performance.service.*;
import com.lootfast.backend.order.entity.*;
import com.lootfast.backend.premium.dto.*;
import com.lootfast.backend.premium.service.PremiumEntitlementService;
import com.lootfast.backend.user.entity.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.transaction.annotation.*;

abstract class AdvancedMerchantPerformanceTests extends MerchantPerformanceTests {
  @Autowired AdvancedPerformanceService advanced;
  @Autowired PremiumEntitlementService premium;
  @MockitoBean AdvancedReportRateLimiter limiter;
  @MockitoBean Clock clock;
  @MockitoSpyBean AdvancedPerformanceRepository advancedRepository;
  User admin;

  @BeforeEach
  void premiumSetup() {
    when(clock.instant()).thenReturn(START.plusSeconds(5));
    admin = user(UserRole.ADMIN);
  }

  String adminSubject() {
    return admin.getId().toString();
  }

  String subject() {
    return owner.getId().toString();
  }

  GrantPremiumRequest request(UUID id, Instant from, Instant to) {
    return new GrantPremiumRequest(id, owner.getPublicId(), from, to, "Trial reports");
  }

  PremiumEntitlement grant() {
    return premium.grant(
        adminSubject(), request(UUID.randomUUID(), START, START.plusSeconds(86400)));
  }

  void error(ErrorCode code, org.junit.jupiter.api.function.Executable action) {
    assertEquals(code, assertThrows(ApiException.class, action).getErrorCode());
  }

  @Test
  void comparesPeriodsAndRatesUsingSnapshotsWithoutIncludingOtherMerchants() {
    grant();
    order(
        deal, 2, START.minusSeconds(86400), START.minusSeconds(86400), OrderStatus.COMPLETED, true);
    order(deal, 2, START.minusSeconds(1), START, OrderStatus.COMPLETED, true);
    order(deal, 3, START, START, OrderStatus.COMPLETED, true);
    order(deal, 1, START, START, OrderStatus.COMPLETED, false);
    order(deal, 1, START, START, OrderStatus.CANCELLED, false);
    order(deal, 1, START, START, OrderStatus.MERCHANT_CANCELLED, false);
    order(deal, 1, START, START, OrderStatus.NO_SHOW, false);
    order(deal, 1, START, START, OrderStatus.RESERVED, false);
    order(
        deal(merchant(user(UserRole.MERCHANT)), "Other"),
        5,
        START,
        START,
        OrderStatus.COMPLETED,
        true);
    jdbc.update(
        "update deals set name='Changed name', original_price=120000,discounted_price=90000 where"
            + " id=?",
        deal.getId());
    var r = advanced.get(subject(), DAY, DAY, null, null, 0, 20);
    assertEquals(DAY.minusDays(1), r.comparisonPeriod().from());
    assertTrue(r.samePeriodLength());
    assertEquals(6, r.current().placedOrders());
    assertEquals(2, r.current().completedOrders());
    assertEquals(1, r.current().customerCancelledOrders());
    assertEquals(1, r.current().merchantCancelledOrders());
    assertEquals(1, r.current().pendingOrders());
    assertEquals(1, r.current().noShowOrders());
    money(250000, r.current().completedRevenue());
    assertEquals(6, r.current().discountedPortions());
    money(100000, r.previous().completedRevenue());
    money(150000, r.comparison().revenue().absoluteChange());
    money(150, r.comparison().revenue().percentChange());
    assertEquals(new BigDecimal("33.33"), r.current().completionRatePercent());
    assertEquals(new BigDecimal("33.33"), r.current().cancellationRatePercent());
    assertEquals(new BigDecimal("16.67"), r.current().noShowRatePercent());
    assertEquals(new BigDecimal("-66.67"), r.comparison().completionRateChangePoints());
    assertEquals(1, r.deals().totalElements());
    assertEquals("Discount meal", r.deals().items().getFirst().name());
    money(250000, r.deals().items().getFirst().completedRevenue());
  }

  @Test
  void zeroBaselineEmptyPeriodAndDifferentLengthComparisonsAreExplicit() {
    grant();
    var empty = advanced.get(subject(), DAY, DAY, null, null, 0, 20);
    assertEquals(0, empty.current().placedOrders());
    assertNull(empty.current().completionRatePercent());
    assertNull(empty.comparison().revenue().percentChange());
    assertTrue(empty.comparison().revenue().zeroBaseline());
    order(deal, 1, START, START, OrderStatus.COMPLETED, true);
    var positive = advanced.get(subject(), DAY, DAY, DAY.minusDays(7), DAY.minusDays(1), 0, 20);
    assertFalse(positive.samePeriodLength());
    assertEquals(7, positive.comparisonPeriod().days());
    assertNull(positive.comparison().revenue().percentChange());
    money(50000, positive.comparison().revenue().absoluteChange());
  }

  @Test
  void pagesDealsAndChecksDateBoundariesAndLimits() {
    grant();
    for (int i = 0; i < 12; i++)
      order(deal(store, "Deal " + i), i == 11 ? 5 : 1, START, START, OrderStatus.COMPLETED, true);
    var page = advanced.get(subject(), DAY, DAY, null, null, 0, 5);
    assertEquals(12, page.deals().totalElements());
    assertEquals(3, page.deals().totalPages());
    assertEquals(5, page.deals().items().size());
    assertEquals("Deal 11", page.deals().items().getFirst().name());
    var second = advanced.get(subject(), DAY, DAY, null, null, 1, 5);
    assertTrue(Collections.disjoint(page.deals().items(), second.deals().items()));
    error(ErrorCode.INVALID_REPORT_RANGE, () -> advanced.get(subject(), DAY, DAY, DAY, DAY, 0, 20));
    error(
        ErrorCode.INVALID_REPORT_RANGE,
        () -> advanced.get(subject(), DAY, DAY, DAY.minusDays(1), null, 0, 20));
    error(
        ErrorCode.INVALID_REPORT_RANGE,
        () -> advanced.get(subject(), DAY, DAY.plusDays(366), null, null, 0, 20));
    error(
        ErrorCode.INVALID_REPORT_RANGE,
        () ->
            advanced.get(
                subject(), LocalDate.of(1, 1, 1), LocalDate.of(1, 1, 1), null, null, 0, 20));
    error(ErrorCode.VALIDATION_FAILED, () -> advanced.get(subject(), DAY, DAY, null, null, 0, 51));
    order(deal, 1, START.minusSeconds(1), START.minusSeconds(1), OrderStatus.COMPLETED, true);
    order(deal, 1, START.plusSeconds(86400), START.plusSeconds(86400), OrderStatus.COMPLETED, true);
    assertEquals(12, advanced.get(subject(), DAY, DAY, null, null, 0, 20).current().placedOrders());
  }

  @Test
  void premiumChecksStartExpiryRevocationAndIdempotentRetries() {
    error(ErrorCode.PREMIUM_REQUIRED, () -> advanced.get(subject(), DAY, DAY, null, null, 0, 20));
    verifyNoInteractions(limiter);
    var req = request(UUID.randomUUID(), START.plusSeconds(10), START.plusSeconds(60));
    var first = premium.grant(adminSubject(), req);
    assertEquals(first, premium.grant(adminSubject(), req));
    assertFalse(premium.available(subject()));
    when(clock.instant()).thenReturn(START.plusSeconds(10));
    assertTrue(premium.available(subject()));
    advanced.get(subject(), DAY, DAY, null, null, 0, 20);
    when(clock.instant()).thenReturn(START.plusSeconds(60));
    assertFalse(premium.available(subject()));
    error(ErrorCode.PREMIUM_REQUIRED, () -> advanced.get(subject(), DAY, DAY, null, null, 0, 20));
    var live = grant();
    assertTrue(premium.available(subject()));
    var revoked = premium.revoke(adminSubject(), live.id(), "Trial ended");
    assertFalse(premium.available(subject()));
    assertEquals(revoked, premium.revoke(adminSubject(), live.id(), "Repeat revoke"));
    assertEquals(
        revoked, premium.grant(adminSubject(), request(live.id(), live.startsAt(), live.endsAt())));
    assertFalse(premium.available(subject()));
    error(
        ErrorCode.PREMIUM_ENTITLEMENT_CONFLICT,
        () ->
            premium.grant(
                adminSubject(),
                new GrantPremiumRequest(
                    live.id(),
                    owner.getPublicId(),
                    live.startsAt(),
                    live.endsAt(),
                    "Different reason")));
  }

  @Test
  void grantsNormalizePostgresTimestampPrecisionForSafeRetries() {
    var request =
        request(
            UUID.randomUUID(),
            START.plusNanos(123456789),
            START.plusSeconds(60).plusNanos(987654321));
    var first = premium.grant(adminSubject(), request);
    assertEquals(
        request.startsAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS), first.startsAt());
    assertEquals(first, premium.grant(adminSubject(), request));
  }

  @Test
  void apiEnforcesPremiumAndAdminPrivilegesAndKeepsBasicReportFree() throws Exception {
    String path = "/api/v1/merchants/me/reports/performance/advanced";
    String ownerToken = "Bearer " + tokens.issueAccessToken(owner).value();
    mvc.perform(get(path).param("from", DAY.toString()).param("to", DAY.toString()))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            get(path)
                .header("Authorization", "Bearer " + tokens.issueAccessToken(buyer).value())
                .param("from", DAY.toString())
                .param("to", DAY.toString()))
        .andExpect(status().isForbidden());
    mvc.perform(
            get(path)
                .header("Authorization", ownerToken)
                .param("from", DAY.toString())
                .param("to", DAY.toString()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("PREMIUM_REQUIRED"));
    mvc.perform(
            get("/api/v1/merchants/me/reports/performance")
                .header("Authorization", ownerToken)
                .param("from", DAY.toString())
                .param("to", DAY.toString()))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/v1/admin/premium/entitlements")
                .header("Authorization", ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());
    grant();
    mvc.perform(
            get(path)
                .header("Authorization", ownerToken)
                .param("from", DAY.toString())
                .param("to", DAY.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.current.placedOrders").value(0));
    mvc.perform(
            get("/api/v1/admin/premium/entitlements")
                .header("Authorization", "Bearer " + tokens.issueAccessToken(admin).value())
                .param("userId", owner.getPublicId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1));
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['" + path + "']").exists());
    error(
        ErrorCode.ACCESS_DENIED,
        () ->
            premium.grant(
                buyer.getId().toString(),
                request(UUID.randomUUID(), START, START.plusSeconds(60))));
    error(
        ErrorCode.ACCESS_DENIED,
        () ->
            premium.grant(
                adminSubject(),
                new GrantPremiumRequest(
                    UUID.randomUUID(),
                    buyer.getPublicId(),
                    START,
                    START.plusSeconds(60),
                    "Trial")));
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void concurrentGrantRetriesCreateOneAuditRecord() throws Exception {
    var req = request(UUID.randomUUID(), START, START.plusSeconds(60));
    var gate = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(2);
    try {
      var a =
          pool.submit(
              () -> {
                gate.await();
                return premium.grant(adminSubject(), req);
              });
      var b =
          pool.submit(
              () -> {
                gate.await();
                return premium.grant(adminSubject(), req);
              });
      gate.countDown();
      assertEquals(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
      assertEquals(1, premium.list(adminSubject(), owner.getPublicId(), 0, 20).totalElements());
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void reportKeepsOneSnapshotWhenAnotherRequestCompletesAnOrder() throws Exception {
    grant();
    order(deal, 1, START, START, OrderStatus.RESERVED, false);
    var first = new AtomicBoolean(true);
    var pool = Executors.newSingleThreadExecutor();
    try {
      doAnswer(
              call -> {
                var result = call.callRealMethod();
                if (first.compareAndSet(true, false)) {
                  pool.submit(
                          () ->
                              jdbc.update(
                                  "update customer_orders set"
                                      + " status='COMPLETED',payment_status='CONFIRMED',finalized_at=?,payment_confirmed_at=?,payment_confirmed_by_user_id=?"
                                      + " where deal_id=?",
                                  java.sql.Timestamp.from(START),
                                  java.sql.Timestamp.from(START),
                                  owner.getId(),
                                  deal.getId()))
                      .get(10, TimeUnit.SECONDS);
                }
                return result;
              })
          .when(advancedRepository)
          .summary(anyLong(), any(), any());
      var snapshot = advanced.get(subject(), DAY, DAY, null, null, 0, 20);
      assertEquals(1, snapshot.current().pendingOrders());
      assertEquals(1, snapshot.deals().items().getFirst().pendingOrders());
      money(0, snapshot.deals().items().getFirst().completedRevenue());
      var next = advanced.get(subject(), DAY, DAY, null, null, 0, 20);
      assertEquals(1, next.current().completedOrders());
      money(50000, next.current().completedRevenue());
    } finally {
      pool.shutdownNow();
    }
  }
}
