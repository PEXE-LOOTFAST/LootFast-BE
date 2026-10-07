package com.lootfast.backend.premium.subscription;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.lootfast.backend.auth.service.JwtService;
import com.lootfast.backend.common.exception.*;
import com.lootfast.backend.content.entity.LegalContent;
import com.lootfast.backend.content.repository.LegalContentRepository;
import com.lootfast.backend.premium.catalog.dto.*;
import com.lootfast.backend.premium.catalog.dto.PlanRequests.*;
import com.lootfast.backend.premium.catalog.service.PlanCatalogService;
import com.lootfast.backend.premium.dto.*;
import com.lootfast.backend.premium.service.PremiumEntitlementService;
import com.lootfast.backend.premium.subscription.dto.SubscriptionResponse.Period;
import com.lootfast.backend.premium.subscription.service.SubscriptionService;
import com.lootfast.backend.user.entity.*;
import com.lootfast.backend.user.repository.UserRepository;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(
    properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureMockMvc
@Transactional
abstract class SubscriptionTests {
  @Autowired SubscriptionService service;
  @Autowired PlanCatalogService catalog;
  @Autowired PremiumEntitlementService grants;
  @Autowired UserRepository users;
  @Autowired LegalContentRepository legal;

  @Autowired
  com.lootfast.backend.merchant.application.repository.MerchantApplicationRepository applications;

  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager transactions;
  @Autowired JwtService tokens;
  @Autowired MockMvc mvc;
  @MockitoBean Clock clock;
  static final Instant NOW = Instant.parse("2030-01-02T00:00:00Z");
  User admin, buyer, other;
  LegalContent terms;
  PlanResponse plan;

  @BeforeEach
  void setup() {
    when(clock.instant()).thenReturn(NOW);
    admin = user(UserRole.ADMIN);
    buyer = user(UserRole.BUYER);
    other = user(UserRole.BUYER);
    terms = new LegalContent(ConsentDocument.TERMS_OF_USE, UUID.randomUUID().toString());
    terms.edit("Terms", "Test only", NOW, admin.getPublicId());
    terms.publish(NOW, admin.getPublicId());
    legal.saveAndFlush(terms);
    var draft =
        catalog.create(
            actor(),
            new Create(
                "SUB_" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT),
                PlanAudience.BUYER,
                config(49000)));
    plan = catalog.change(actor(), draft.id(), new Change(0L, "Launch"), true);
  }

  String actor() {
    return admin.getId().toString();
  }

  String subject() {
    return buyer.getId().toString();
  }

  Configuration config(long price) {
    return new Configuration(
        "Buyer Premium",
        "Saved filters",
        price,
        30,
        Set.of(PremiumBenefit.BUYER_SAVED_FILTERS),
        terms.getId());
  }

  User user(UserRole role) {
    return users.saveAndFlush(
        new User(
            "Test",
            "Account",
            UUID.randomUUID() + "@test.local",
            null,
            null,
            role,
            UserStatus.VERIFIED));
  }

  Period buy(UUID ref) {
    return service.applyConfirmedPurchase(buyer.getPublicId(), ref, plan.id(), clock.instant());
  }

  void error(ErrorCode code, Runnable action) {
    assertEquals(code, assertThrows(ApiException.class, action::run).getErrorCode());
  }

  @Test
  void purchaseAndRenewalCreateImmutablePeriodsAndConnectedCoverage() {
    var first = buy(UUID.randomUUID());
    var second = buy(UUID.randomUUID());
    assertEquals(NOW, first.startsAt());
    assertEquals(first.endsAt(), second.startsAt());
    assertEquals(NOW.plus(Duration.ofDays(60)), second.endsAt());
    var summary = service.benefits(subject(), PlanAudience.BUYER);
    assertEquals("ACTIVE", summary.status());
    assertEquals(second.endsAt(), summary.paidEndsAt());
    var benefit = summary.benefits().getFirst();
    assertTrue(benefit.enabled());
    assertEquals(Set.of("PURCHASE"), benefit.activeSources());
    assertEquals(second.endsAt(), benefit.effectiveUntil());
    assertEquals(2, service.periods(subject(), PlanAudience.BUYER, 0, 1).totalElements());
    assertEquals(
        0, service.periods(other.getId().toString(), PlanAudience.BUYER, 0, 20).totalElements());
  }

  @Test
  void exactExpiryDisablesAccessAndNewPurchaseStartsNow() {
    var first = buy(UUID.randomUUID());
    when(clock.instant()).thenReturn(first.endsAt());
    assertEquals("EXPIRED", service.benefits(subject(), PlanAudience.BUYER).status());
    assertFalse(service.benefits(subject(), PlanAudience.BUYER).benefits().getFirst().enabled());
    when(clock.instant()).thenReturn(first.endsAt().plus(Duration.ofDays(5)));
    assertEquals(clock.instant(), buy(UUID.randomUUID()).startsAt());
  }

  @Test
  void manualTrialDoesNotDelayPaidPeriodAndSourcesStaySeparate() {
    var trial =
        grants.grant(
            actor(),
            new GrantPremiumRequest(
                UUID.randomUUID(),
                buyer.getPublicId(),
                NOW,
                NOW.plus(Duration.ofDays(60)),
                "Trial",
                PremiumBenefit.BUYER_SAVED_FILTERS));
    assertEquals("MANUAL", trial.source());
    assertNull(trial.subscriptionPeriodId());
    assertEquals("NONE", service.benefits(subject(), PlanAudience.BUYER).status());
    assertTrue(service.benefits(subject(), PlanAudience.BUYER).benefits().getFirst().enabled());
    var paid = buy(UUID.randomUUID());
    assertEquals(NOW, paid.startsAt());
    var summary = service.benefits(subject(), PlanAudience.BUYER);
    assertEquals(paid.endsAt(), summary.paidEndsAt());
    assertEquals(Set.of("MANUAL", "PURCHASE"), summary.benefits().getFirst().activeSources());
    assertEquals(trial.endsAt(), summary.benefits().getFirst().effectiveUntil());
    grants.revoke(actor(), trial.id(), "End trial");
    assertTrue(service.benefits(subject(), PlanAudience.BUYER).benefits().getFirst().enabled());
  }

  @Test
  void futureSeparatedGrantDoesNotExtendCurrentCoverage() {
    var paid = buy(UUID.randomUUID());
    grants.grant(
        actor(),
        new GrantPremiumRequest(
            UUID.randomUUID(),
            buyer.getPublicId(),
            paid.endsAt().plusSeconds(1),
            paid.endsAt().plus(Duration.ofDays(30)),
            "Later",
            PremiumBenefit.BUYER_SAVED_FILTERS));
    assertEquals(
        paid.endsAt(),
        service.benefits(subject(), PlanAudience.BUYER).benefits().getFirst().effectiveUntil());
    when(clock.instant()).thenReturn(paid.endsAt());
    assertFalse(service.benefits(subject(), PlanAudience.BUYER).benefits().getFirst().enabled());
  }

  @Test
  void idempotencyPreservesPeriodAndRejectsDifferentPayload() {
    var ref = UUID.randomUUID();
    var first = buy(ref);
    when(clock.instant()).thenReturn(NOW.plus(Duration.ofDays(90)));
    assertEquals(first, service.applyConfirmedPurchase(buyer.getPublicId(), ref, plan.id(), NOW));
    error(
        ErrorCode.PREMIUM_SUBSCRIPTION_CONFLICT,
        () -> service.applyConfirmedPurchase(other.getPublicId(), ref, plan.id(), NOW));
    error(
        ErrorCode.PREMIUM_SUBSCRIPTION_CONFLICT,
        () ->
            service.applyConfirmedPurchase(
                buyer.getPublicId(), ref, plan.id(), NOW.plusSeconds(1)));
    assertEquals(1, service.periods(subject(), PlanAudience.BUYER, 0, 20).totalElements());
  }

  @Test
  void archivedPublishedConfigurationStillHonoredAndSnapshotsKeepOldPrice() {
    var first = buy(UUID.randomUUID());
    var next = catalog.draft(actor(), plan.code(), config(59000));
    catalog.change(actor(), next.id(), new Change(0L, "Price update"), true);
    assertEquals(49000, buy(UUID.randomUUID()).priceVnd());
    assertEquals(
        49000, service.periods(subject(), PlanAudience.BUYER, 0, 20).items().getLast().priceVnd());
    assertEquals(plan.id(), first.planRevisionId());
    var draft = catalog.draft(actor(), plan.code(), config(69000));
    error(
        ErrorCode.PREMIUM_PLAN_CONFLICT,
        () ->
            service.applyConfirmedPurchase(
                buyer.getPublicId(), UUID.randomUUID(), draft.id(), NOW));
  }

  @Test
  void roleIsolationAndLockSuspendAccessWithoutDiscardingReceipt() {
    var merchantPlan =
        catalog.create(
            actor(),
            new Create(
                plan.code() + "_M",
                PlanAudience.MERCHANT,
                new Configuration(
                    "Merchant",
                    "Reports",
                    199000L,
                    30,
                    Set.of(PremiumBenefit.MERCHANT_ADVANCED_REPORTS),
                    terms.getId())));
    catalog.change(actor(), merchantPlan.id(), new Change(0L, "Launch"), true);
    error(
        ErrorCode.ACCESS_DENIED,
        () ->
            service.applyConfirmedPurchase(
                buyer.getPublicId(), UUID.randomUUID(), merchantPlan.id(), NOW));
    buyer.lockAccount("Test", NOW);
    users.saveAndFlush(buyer);
    var paid = buy(UUID.randomUUID());
    assertNotNull(paid.id());
    var summary = service.benefits(subject(), PlanAudience.BUYER);
    assertEquals("SUSPENDED", summary.status());
    assertFalse(summary.benefits().getFirst().enabled());
    assertEquals("NONE", service.benefits(subject(), PlanAudience.MERCHANT).status());
  }

  @Test
  void paidGrantsCannotBeRevokedOrReusedThroughManualAdminFlow() {
    var paid = buy(UUID.randomUUID());
    var id =
        jdbc.queryForObject(
            "select id from premium_entitlements where subscription_period_id=?",
            UUID.class,
            paid.id());
    error(ErrorCode.PREMIUM_SUBSCRIPTION_CONFLICT, () -> grants.revoke(actor(), id, "Stop"));
    error(
        ErrorCode.PREMIUM_ENTITLEMENT_CONFLICT,
        () ->
            grants.grant(
                actor(),
                new GrantPremiumRequest(
                    id,
                    buyer.getPublicId(),
                    paid.startsAt(),
                    paid.endsAt(),
                    "Confirmed Premium purchase",
                    PremiumBenefit.BUYER_SAVED_FILTERS)));
    assertTrue(service.benefits(subject(), PlanAudience.BUYER).benefits().getFirst().enabled());
  }

  @Test
  void authenticatedOwnedApiAndPagination() throws Exception {
    buy(UUID.randomUUID());
    String path = "/api/v1/premium/me/subscriptions";
    mvc.perform(get(path)).andExpect(status().isUnauthorized());
    String token = "Bearer " + tokens.issueAccessToken(buyer).value();
    mvc.perform(get(path).header("Authorization", token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].audience").value("BUYER"));
    mvc.perform(
            get(path + "/BUYER/periods")
                .header("Authorization", "Bearer " + tokens.issueAccessToken(other).value()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(0));
    mvc.perform(get(path + "/BUYER/periods").header("Authorization", token).param("size", "51"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            get("/api/v1/premium/me/benefits")
                .header("Authorization", token)
                .param("audience", "BUYER"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.benefits[0].enabled").value(true));
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/premium/me/subscriptions'].get").exists());
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void callerTransactionRequiredAndAllocationRollsBackAtomically() {
    var ref = UUID.randomUUID();
    try {
      assertThrows(IllegalTransactionStateException.class, () -> buy(ref));
      assertThrows(
          IllegalStateException.class,
          () ->
              new TransactionTemplate(transactions)
                  .execute(
                      status -> {
                        buy(ref);
                        throw new IllegalStateException("Simulated payment transaction failure");
                      }));
      assertEquals(
          0,
          jdbc.queryForObject(
              "select count(*) from premium_subscription_periods where purchase_reference=?",
              Integer.class,
              ref));
      assertEquals(
          0,
          jdbc.queryForObject(
              "select count(*) from premium_subscriptions where user_id=?",
              Integer.class,
              buyer.getId()));
      assertEquals(
          0,
          jdbc.queryForObject(
              "select count(*) from premium_entitlements where user_id=?",
              Integer.class,
              buyer.getId()));
    } finally {
      cleanup();
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void concurrentRenewalsAndDuplicateCallbacksOnlyAllocateOnce() throws Exception {
    var pool = Executors.newFixedThreadPool(2);
    try {
      for (boolean duplicate : new boolean[] {false, true}) {
        var gate = new CountDownLatch(1);
        var ref = UUID.randomUUID();
        var jobs = new ArrayList<Future<Period>>();
        for (int i = 0; i < 2; i++) {
          var reference = duplicate ? ref : UUID.randomUUID();
          jobs.add(
              pool.submit(
                  () -> {
                    gate.await();
                    return new TransactionTemplate(transactions).execute(status -> buy(reference));
                  }));
        }
        gate.countDown();
        var a = jobs.get(0).get(10, TimeUnit.SECONDS);
        var b = jobs.get(1).get(10, TimeUnit.SECONDS);
        if (duplicate) assertEquals(a, b);
        else assertTrue(a.endsAt().equals(b.startsAt()) || b.endsAt().equals(a.startsAt()));
      }
      assertEquals(3, service.periods(subject(), PlanAudience.BUYER, 0, 20).totalElements());
      assertEquals(
          NOW.plus(Duration.ofDays(90)),
          service.benefits(subject(), PlanAudience.BUYER).paidEndsAt());
    } finally {
      pool.shutdownNow();
      cleanup();
    }
  }

  @Test
  void bothRolesKeepIndependentPaidClocksAndMerchantApprovalGatesBenefits() {
    buyer.addRole(UserRole.MERCHANT);
    users.saveAndFlush(buyer);
    var m =
        catalog.create(
            actor(),
            new Create(
                plan.code() + "_M",
                PlanAudience.MERCHANT,
                new Configuration(
                    "Merchant",
                    "Reports",
                    199000L,
                    30,
                    Set.of(PremiumBenefit.MERCHANT_ADVANCED_REPORTS),
                    terms.getId())));
    catalog.change(actor(), m.id(), new Change(0L, "Launch"), true);
    var app =
        new com.lootfast.backend.merchant.application.entity.MerchantApplication(
            buyer, "1234567890", UUID.randomUUID());
    app.markPending("test/license", "pdf", NOW);
    applications.saveAndFlush(app);
    var paid = service.applyConfirmedPurchase(buyer.getPublicId(), UUID.randomUUID(), m.id(), NOW);
    assertEquals("SUSPENDED", service.benefits(subject(), PlanAudience.MERCHANT).status());
    app.approve(admin, NOW);
    applications.saveAndFlush(app);
    assertTrue(service.benefits(subject(), PlanAudience.MERCHANT).benefits().getFirst().enabled());
    buy(UUID.randomUUID());
    buy(UUID.randomUUID());
    assertEquals(
        NOW.plus(Duration.ofDays(60)),
        service.benefits(subject(), PlanAudience.BUYER).paidEndsAt());
    assertEquals(paid.endsAt(), service.benefits(subject(), PlanAudience.MERCHANT).paidEndsAt());
    assertEquals(1, service.periods(subject(), PlanAudience.MERCHANT, 0, 20).totalElements());
  }

  @Test
  void invalidTimeAndPagingFailWithoutAllocation() {
    error(
        ErrorCode.VALIDATION_FAILED,
        () ->
            service.applyConfirmedPurchase(
                buyer.getPublicId(), UUID.randomUUID(), plan.id(), NOW.plusSeconds(1)));
    error(
        ErrorCode.VALIDATION_FAILED,
        () -> service.applyConfirmedPurchase(buyer.getPublicId(), null, plan.id(), NOW));
    error(
        ErrorCode.VALIDATION_FAILED,
        () -> service.periods(subject(), PlanAudience.BUYER, Integer.MAX_VALUE, 50));
    error(ErrorCode.VALIDATION_FAILED, () -> service.benefits(subject(), null));
    assertEquals(0, service.periods(subject(), PlanAudience.BUYER, 0, 20).totalElements());
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void simultaneousReferenceReuseByDifferentUsersHasOneOwner() throws Exception {
    var pool = Executors.newFixedThreadPool(2);
    var gate = new CountDownLatch(1);
    var ref = UUID.randomUUID();
    try {
      var jobs = new ArrayList<Future<String>>();
      for (var u : List.of(buyer, other))
        jobs.add(
            pool.submit(
                () -> {
                  gate.await();
                  try {
                    new TransactionTemplate(transactions)
                        .execute(
                            status ->
                                service.applyConfirmedPurchase(
                                    u.getPublicId(), ref, plan.id(), NOW));
                    return "OK";
                  } catch (ApiException e) {
                    return e.getErrorCode().name();
                  }
                }));
      gate.countDown();
      var results =
          List.of(jobs.get(0).get(10, TimeUnit.SECONDS), jobs.get(1).get(10, TimeUnit.SECONDS));
      assertEquals(1, Collections.frequency(results, "OK"));
      assertEquals(1, Collections.frequency(results, "PREMIUM_SUBSCRIPTION_CONFLICT"));
      assertEquals(
          1,
          jdbc.queryForObject(
              "select count(*) from premium_subscription_periods where purchase_reference=?",
              Integer.class,
              ref));
      assertEquals(
          1,
          jdbc.queryForObject(
              "select count(*) from premium_subscriptions where user_id in (?,?)",
              Integer.class,
              buyer.getId(),
              other.getId()));
    } finally {
      pool.shutdownNow();
      cleanup();
    }
  }

  void cleanup() {
    jdbc.update(
        "delete from premium_entitlements where user_id in (?,?,?)",
        buyer.getId(),
        other.getId(),
        admin.getId());
    jdbc.update(
        "delete from premium_subscription_periods where subscription_id in(select id from"
            + " premium_subscriptions where user_id in (?,?,?))",
        buyer.getId(),
        other.getId(),
        admin.getId());
    jdbc.update(
        "delete from premium_subscriptions where user_id in (?,?,?)",
        buyer.getId(),
        other.getId(),
        admin.getId());
    jdbc.update("delete from premium_plan_audits where plan_revision_id=?", plan.id());
    jdbc.update("delete from premium_plan_benefits where plan_revision_id=?", plan.id());
    jdbc.update("delete from premium_plan_revisions where id=?", plan.id());
    jdbc.update("delete from premium_plans where code=?", plan.code());
    jdbc.update("delete from content_legal_versions where id=?", terms.getId());
    jdbc.update(
        "delete from users where id in (?,?,?)", buyer.getId(), other.getId(), admin.getId());
  }
}
