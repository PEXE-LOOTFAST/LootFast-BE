package com.lootfast.backend.premium.checkout;

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
import com.lootfast.backend.premium.checkout.dto.CheckoutDtos;
import com.lootfast.backend.premium.checkout.service.*;
import com.lootfast.backend.premium.dto.PremiumBenefit;
import com.lootfast.backend.user.entity.*;
import com.lootfast.backend.user.repository.UserRepository;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.*;

@SpringBootTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "lootfast.vnpay.enabled=true",
      "lootfast.vnpay.tmn-code=TEST0001",
      "lootfast.vnpay.hash-secret=test-only-secret",
      "lootfast.vnpay.return-url=https://example.test/premium/result"
    })
@AutoConfigureMockMvc
@Transactional
abstract class CheckoutTests {
  @Autowired CheckoutService service;
  @Autowired PlanCatalogService catalog;
  @Autowired UserRepository users;
  @Autowired LegalContentRepository legal;
  @Autowired JdbcTemplate jdbc;
  @Autowired JwtService tokens;
  @Autowired MockMvc mvc;
  @MockitoBean Clock clock;
  @MockitoSpyBean VnpayCheckout gateway;
  User admin, buyer, other;
  LegalContent terms;
  PlanResponse plan;
  static final Instant NOW = Instant.parse("2030-01-02T00:00:00Z");

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
    var d =
        catalog.create(
            actor(),
            new Create(
                "CHECK_" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT),
                PlanAudience.BUYER,
                config(49000)));
    plan = catalog.change(actor(), d.id(), new Change(0L, "Launch"), true);
  }

  String actor() {
    return admin.getId().toString();
  }

  String subject() {
    return buyer.getId().toString();
  }

  Configuration config(long p) {
    return new Configuration(
        "Buyer Premium",
        "Filters",
        p,
        30,
        Set.of(PremiumBenefit.BUYER_SAVED_FILTERS),
        terms.getId());
  }

  User user(UserRole r) {
    return users.saveAndFlush(
        new User(
            "Test",
            "Account",
            UUID.randomUUID() + "@test.local",
            null,
            null,
            r,
            UserStatus.VERIFIED));
  }

  CheckoutDtos.Create request() {
    return new CheckoutDtos.Create(plan.id(), terms.getId(), true);
  }

  CheckoutDtos.Order create(UUID key) {
    return service.create(subject(), key, request());
  }

  void error(ErrorCode code, Runnable action) {
    assertEquals(code, assertThrows(ApiException.class, action::run).getErrorCode());
  }

  @Test
  void snapshotAndIdempotencySurviveReplacement() {
    var key = UUID.randomUUID();
    var o = create(key);
    assertEquals(plan, o.plan());
    assertEquals(NOW, o.acceptedAt());
    var next = catalog.draft(actor(), plan.code(), config(59000));
    catalog.change(actor(), next.id(), new Change(0L, "Price update"), true);
    assertEquals(o, create(key));
    assertTrue(
        service.pay(subject(), o.id(), "127.0.0.1").paymentUrl().contains("vnp_Amount=4900000"));
    error(ErrorCode.PREMIUM_PLAN_NOT_FOUND, () -> create(UUID.randomUUID()));
    error(
        ErrorCode.IDEMPOTENCY_CONFLICT,
        () ->
            service.create(
                subject(), key, new CheckoutDtos.Create(next.id(), terms.getId(), true)));
  }

  @Test
  void oneAttemptAndNoEntitlement() {
    var o = create(UUID.randomUUID());
    var p = service.pay(subject(), o.id(), "127.0.0.1");
    assertEquals(p, service.pay(subject(), o.id(), "192.168.1.1"));
    assertFalse(p.toString().contains("vnp_SecureHash"));
    assertEquals(
        1,
        jdbc.queryForObject(
            "select count(*) from premium_payment_attempts where order_id=?",
            Integer.class,
            o.id()));
    assertEquals(
        0,
        jdbc.queryForObject(
            "select count(*) from premium_entitlements where user_id=?",
            Integer.class,
            buyer.getId()));
    assertEquals("PENDING_PAYMENT", service.get(subject(), o.id()).status());
  }

  @Test
  void quotasOwnershipAndPaging() {
    var o = create(UUID.randomUUID());
    error(ErrorCode.PREMIUM_CHECKOUT_CONFLICT, () -> create(UUID.randomUUID()));
    error(ErrorCode.PREMIUM_ORDER_NOT_FOUND, () -> service.get(other.getId().toString(), o.id()));
    error(
        ErrorCode.PREMIUM_ORDER_NOT_FOUND,
        () -> service.pay(other.getId().toString(), o.id(), "127.0.0.1"));
    assertEquals(0, service.list(other.getId().toString(), 0, 20).totalElements());
    error(ErrorCode.VALIDATION_FAILED, () -> service.list(subject(), 0, 51));
  }

  @Test
  void exactExpiryIsNotPaymentFailure() {
    var o = create(UUID.randomUUID());
    when(clock.instant()).thenReturn(o.expiresAt());
    assertEquals("EXPIRED", service.get(subject(), o.id()).status());
    error(ErrorCode.PAYMENT_STATE_CONFLICT, () -> service.pay(subject(), o.id(), "127.0.0.1"));
    var next = create(UUID.randomUUID());
    service.pay(subject(), next.id(), "127.0.0.1");
    when(clock.instant()).thenReturn(next.expiresAt());
    assertEquals("AWAITING_RECONCILIATION", service.get(subject(), next.id()).status());
    error(ErrorCode.PAYMENT_STATE_CONFLICT, () -> service.pay(subject(), next.id(), "127.0.0.1"));
  }

  @Test
  void consentAndAccountLock() {
    error(
        ErrorCode.VALIDATION_FAILED,
        () ->
            service.create(
                subject(),
                UUID.randomUUID(),
                new CheckoutDtos.Create(plan.id(), terms.getId(), false)));
    error(
        ErrorCode.CONSENT_VERSION_OUTDATED,
        () ->
            service.create(
                subject(),
                UUID.randomUUID(),
                new CheckoutDtos.Create(plan.id(), UUID.randomUUID(), true)));
    buyer.lockAccount("Test", NOW);
    users.saveAndFlush(buyer);
    error(ErrorCode.ACCOUNT_LOCKED, () -> create(UUID.randomUUID()));
  }

  @Test
  void disabledGatewayOrInvalidIpIsAtomic() {
    var o = create(UUID.randomUUID());
    doThrow(new ApiException(ErrorCode.PAYMENT_NOT_CONFIGURED)).when(gateway).requireEnabled();
    error(ErrorCode.PAYMENT_NOT_CONFIGURED, () -> service.pay(subject(), o.id(), "127.0.0.1"));
    reset(gateway);
    error(ErrorCode.VALIDATION_FAILED, () -> service.pay(subject(), o.id(), "attacker.example"));
    assertNull(service.get(subject(), o.id()).attempt());
    assertEquals(
        1,
        jdbc.queryForObject(
            "select count(*) from premium_order_events where order_id=?", Integer.class, o.id()));
  }

  @Test
  void roleAndApproval() {
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
    var r = new CheckoutDtos.Create(m.id(), terms.getId(), true);
    error(ErrorCode.ACCESS_DENIED, () -> service.create(subject(), UUID.randomUUID(), r));
    buyer.addRole(UserRole.MERCHANT);
    users.saveAndFlush(buyer);
    error(ErrorCode.MERCHANT_NOT_APPROVED, () -> service.create(subject(), UUID.randomUUID(), r));
  }

  @Test
  void httpContract() throws Exception {
    String path = "/api/v1/premium/orders",
        token = "Bearer " + tokens.issueAccessToken(buyer).value();
    mvc.perform(get(path)).andExpect(status().isUnauthorized());
    mvc.perform(
            post(path)
                .header("Authorization", token)
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"planRevisionId\":\""
                        + plan.id()
                        + "\",\"termsDocumentId\":\""
                        + terms.getId()
                        + "\",\"acceptTerms\":true,\"amount\":1}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.plan.priceVnd").value(49000));
    var o = service.list(subject(), 0, 20).items().getFirst();
    mvc.perform(post(path + "/" + o.id() + "/payment-attempts").header("Authorization", token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.paymentUrl").isString());
    mvc.perform(
            get(path + "/" + o.id())
                .header("Authorization", "Bearer " + tokens.issueAccessToken(other).value()))
        .andExpect(status().isNotFound());
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/premium/orders'].post").exists());
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void concurrentRequests() throws Exception {
    var pool = Executors.newFixedThreadPool(2);
    var key = UUID.randomUUID();
    try {
      var gate = new CountDownLatch(1);
      var jobs = new ArrayList<Future<CheckoutDtos.Order>>();
      for (int i = 0; i < 2; i++)
        jobs.add(
            pool.submit(
                () -> {
                  gate.await();
                  return create(key);
                }));
      gate.countDown();
      var a = jobs.get(0).get(10, TimeUnit.SECONDS);
      assertEquals(a, jobs.get(1).get(10, TimeUnit.SECONDS));
      var payGate = new CountDownLatch(1);
      var payments = new ArrayList<Future<CheckoutDtos.Payment>>();
      for (int i = 0; i < 2; i++)
        payments.add(
            pool.submit(
                () -> {
                  payGate.await();
                  return service.pay(subject(), a.id(), "127.0.0.1");
                }));
      payGate.countDown();
      assertEquals(
          payments.get(0).get(10, TimeUnit.SECONDS), payments.get(1).get(10, TimeUnit.SECONDS));
      assertEquals(1, service.list(subject(), 0, 20).totalElements());
    } finally {
      pool.shutdownNow();
      cleanup();
    }
  }

  @Test
  void hourlyQuota() {
    for (int i = 0; i < 10; i++) {
      var o = create(UUID.randomUUID());
      jdbc.update("update premium_orders set status='FAILED' where id=?", o.id());
    }
    error(ErrorCode.PAYMENT_RETRY_LATER, () -> create(UUID.randomUUID()));
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void distinctKeysCannotBypassPendingQuota() throws Exception {
    var pool = Executors.newFixedThreadPool(2);
    var gate = new CountDownLatch(1);
    try {
      var jobs = new ArrayList<Future<String>>();
      for (int i = 0; i < 2; i++)
        jobs.add(
            pool.submit(
                () -> {
                  gate.await();
                  try {
                    create(UUID.randomUUID());
                    return "OK";
                  } catch (ApiException e) {
                    return e.getErrorCode().name();
                  }
                }));
      gate.countDown();
      var results =
          List.of(jobs.get(0).get(10, TimeUnit.SECONDS), jobs.get(1).get(10, TimeUnit.SECONDS));
      assertEquals(1, Collections.frequency(results, "OK"));
      assertEquals(1, Collections.frequency(results, "PREMIUM_CHECKOUT_CONFLICT"));
      assertEquals(1, service.list(subject(), 0, 20).totalElements());
    } finally {
      pool.shutdownNow();
      cleanup();
    }
  }

  void cleanup() {
    jdbc.update(
        "delete from premium_order_events where order_id in(select id from premium_orders where"
            + " user_id=?)",
        buyer.getId());
    jdbc.update(
        "delete from premium_payment_attempts where order_id in(select id from premium_orders where"
            + " user_id=?)",
        buyer.getId());
    jdbc.update("delete from premium_orders where user_id=?", buyer.getId());
    jdbc.update("delete from premium_plan_audits where plan_revision_id=?", plan.id());
    jdbc.update("delete from premium_plan_benefits where plan_revision_id=?", plan.id());
    jdbc.update("delete from premium_plan_revisions where id=?", plan.id());
    jdbc.update("delete from premium_plans where code=?", plan.code());
    jdbc.update("delete from content_legal_versions where id=?", terms.getId());
    jdbc.update(
        "delete from users where id in (?,?,?)", buyer.getId(), other.getId(), admin.getId());
  }
}
