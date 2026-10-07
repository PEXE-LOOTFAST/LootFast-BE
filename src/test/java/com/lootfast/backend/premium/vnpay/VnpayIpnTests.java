package com.lootfast.backend.premium.vnpay;

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
abstract class VnpayIpnTests {
  @Autowired CheckoutService service;
  @Autowired PlanCatalogService catalog;
  @Autowired UserRepository users;
  @Autowired LegalContentRepository legal;
  @Autowired JdbcTemplate jdbc;
  @Autowired JwtService tokens;
  @Autowired MockMvc mvc;
  @MockitoBean Clock clock;
  @MockitoSpyBean VnpayCheckout gateway;
  @Autowired VnpayIpnService ipn;
  @Autowired VnpayVerifier verifier;
  @Autowired com.lootfast.backend.premium.subscription.service.SubscriptionService subscriptions;
  @MockitoSpyBean VnpayReceiptRepository receipts;
  @Autowired com.lootfast.backend.premium.vnpay.query.QueryStore queryStore;
  @Autowired com.lootfast.backend.premium.vnpay.query.QueryProtocol queryProtocol;
  @MockitoSpyBean com.lootfast.backend.premium.vnpay.query.QuerySettings querySettings;
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

  CheckoutDtos.Order paidIntent() {
    var o = create(UUID.randomUUID());
    service.pay(subject(), o.id(), "127.0.0.1");
    return service.get(subject(), o.id());
  }

  Map<String, String[]> callback(CheckoutDtos.Order o, String txn) {
    var f = new TreeMap<String, String>();
    f.put("vnp_TmnCode", "TEST0001");
    f.put("vnp_TxnRef", o.attempt().reference());
    f.put("vnp_Amount", Long.toString(o.plan().priceVnd() * 100));
    f.put("vnp_TransactionNo", txn);
    f.put("vnp_ResponseCode", "00");
    f.put("vnp_TransactionStatus", "00");
    f.put("vnp_BankCode", "NCB");
    f.put("vnp_OrderInfo", "LootFast Premium " + o.attempt().reference());
    f.put("vnp_PayDate", "20300102070000");
    return signed(f);
  }

  Map<String, String[]> signed(Map<String, String> f) {
    try {
      var sorted = new TreeMap<>(f);
      sorted.remove("vnp_SecureHash");
      String canonical =
          sorted.entrySet().stream()
              .map(
                  e ->
                      java.net.URLEncoder.encode(
                              e.getKey(), java.nio.charset.StandardCharsets.US_ASCII)
                          + "="
                          + java.net.URLEncoder.encode(
                              e.getValue(), java.nio.charset.StandardCharsets.US_ASCII))
              .collect(java.util.stream.Collectors.joining("&"));
      var h = javax.crypto.Mac.getInstance("HmacSHA512");
      h.init(
          new javax.crypto.spec.SecretKeySpec(
              "test-only-secret".getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA512"));
      sorted.put(
          "vnp_SecureHash",
          HexFormat.of()
              .formatHex(
                  h.doFinal(canonical.getBytes(java.nio.charset.StandardCharsets.US_ASCII))));
      var p = new LinkedHashMap<String, String[]>();
      sorted.forEach((k, v) -> p.put(k, new String[] {v}));
      return p;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  Map<String, String[]> change(Map<String, String[]> data, String key, String value) {
    var f = new TreeMap<String, String>();
    data.forEach((k, v) -> f.put(k, v[0]));
    f.put(key, value);
    return signed(f);
  }

  VnpayAck process(Map<String, String[]> data) {
    return ipn.process(verifier.verify(data, 1000));
  }

  long periods() {
    return jdbc.queryForObject(
        "select count(*) from premium_subscription_periods where premium_order_id in(select id from"
            + " premium_orders where user_id in (?,?))",
        Long.class,
        buyer.getId(),
        other.getId());
  }

  @Test
  void successIsAtomicAndRetryNeverExtendsAgain() {
    var o = paidIntent();
    var data = callback(o, "10001");
    assertEquals("00", process(data).code());
    assertEquals("02", process(data).code());
    assertEquals("FULFILLED", service.get(subject(), o.id()).status());
    assertEquals("SUCCEEDED", service.get(subject(), o.id()).attempt().status());
    assertEquals(1, periods());
    assertEquals(
        NOW.plusSeconds(30 * 86400L),
        subscriptions.benefits(subject(), PlanAudience.BUYER).paidEndsAt());
    assertEquals(
        1,
        jdbc.queryForObject(
            "select count(*) from premium_payment_receipts where order_id=?",
            Integer.class,
            o.id()));
  }

  @Test
  void badSignatureMerchantAndUnknownReferenceDoNotGrant() {
    var o = paidIntent();
    var data = callback(o, "10002");
    data.put("vnp_SecureHash", new String[] {"0".repeat(128)});
    assertEquals("97", assertThrows(InvalidVnpayNotification.class, () -> process(data)).code());
    var good = callback(o, "10002");
    assertEquals(
        "97",
        assertThrows(
                InvalidVnpayNotification.class,
                () -> process(change(good, "vnp_TmnCode", "OTHER001")))
            .code());
    assertEquals("01", process(change(good, "vnp_TxnRef", "unknown")).code());
    assertEquals(0, periods());
  }

  @Test
  void amountMismatchRetriedWithoutFillingReceiptTable() {
    var o = paidIntent();
    var data = change(callback(o, "10003"), "vnp_Amount", "1");
    assertEquals("04", process(data).code());
    assertEquals("04", process(data).code());
    assertEquals(0, periods());
    assertEquals(
        1,
        jdbc.queryForObject(
            "select count(*) from premium_payment_receipts where order_id=?",
            Integer.class,
            o.id()));
  }

  @Test
  void failureThenSuccessAndOlderFailureCannotDowngrade() {
    var o = paidIntent();
    var success = callback(o, "10004");
    var failed = change(change(success, "vnp_ResponseCode", "24"), "vnp_TransactionStatus", "02");
    assertEquals("00", process(failed).code());
    assertEquals("FAILED", service.get(subject(), o.id()).status());
    assertEquals("00", process(success).code());
    var older = change(failed, "vnp_OrderInfo", "Older notice");
    assertEquals("02", process(older).code());
    assertEquals("FULFILLED", service.get(subject(), o.id()).status());
    assertEquals(1, periods());
  }

  @Test
  void lateSuccessAndLockedAccountStillRecordPaidPeriod() {
    var o = paidIntent();
    when(clock.instant()).thenReturn(NOW.plusSeconds(3600));
    buyer.lockAccount("Test", NOW);
    users.saveAndFlush(buyer);
    assertEquals("00", process(callback(o, "10005")).code());
    assertEquals(1, periods());
    var s = subscriptions.benefits(subject(), PlanAudience.BUYER);
    assertEquals("SUSPENDED", s.status());
    assertFalse(s.benefits().getFirst().enabled());
    assertEquals(NOW.plusSeconds(3600 + 30 * 86400L), s.paidEndsAt());
  }

  @Test
  void providerTransactionCannotFundTwoOrders() {
    var a = paidIntent();
    var b = service.create(other.getId().toString(), UUID.randomUUID(), request());
    service.pay(other.getId().toString(), b.id(), "127.0.0.1");
    b = service.get(other.getId().toString(), b.id());
    assertEquals("00", process(callback(a, "10006")).code());
    assertEquals("00", process(callback(b, "10006")).code());
    assertEquals("REVIEW_REQUIRED", service.get(other.getId().toString(), b.id()).status());
    assertEquals(1, periods());
  }

  @Test
  void secondRealTransactionOnFulfilledOrderCreatesReview() {
    var o = paidIntent();
    process(callback(o, "10007"));
    assertEquals("00", process(callback(o, "10008")).code());
    assertEquals(1, periods());
    assertEquals("FULFILLED", service.get(subject(), o.id()).status());
    assertEquals(
        1,
        jdbc.queryForObject(
            "select count(*) from premium_payment_receipts where order_id=? and"
                + " outcome='REVIEW_REQUIRED'",
            Integer.class,
            o.id()));
  }

  @Test
  void pendingAndSuspiciousDoNotGrant() {
    var o = paidIntent();
    var data = callback(o, "10009");
    var pending = change(change(data, "vnp_ResponseCode", "99"), "vnp_TransactionStatus", "01");
    assertEquals("99", process(pending).code());
    assertEquals("99", process(pending).code());
    var suspicious = change(change(data, "vnp_ResponseCode", "07"), "vnp_TransactionStatus", "07");
    assertEquals("00", process(suspicious).code());
    assertEquals(0, periods());
    assertEquals("REVIEW_REQUIRED", service.get(subject(), o.id()).status());
  }

  @Test
  void callbackDoesNotHonorFrontendSuccessAndRejectsDuplicates() throws Exception {
    var o = paidIntent();
    var data = callback(o, "10010");
    var req = get("/api/v1/payments/vnpay/ipn");
    data.forEach(req::param);
    req.param("vnp_Amount", "1");
    mvc.perform(req).andExpect(status().isOk()).andExpect(jsonPath("$.RspCode").value("99"));
    mvc.perform(get("/api/v1/payments/vnpay/ipn").param("success", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.RspCode").value("99"));
    assertEquals(0, periods());
  }

  @Test
  void returnUrlOnlyProvidesOrderPointer() throws Exception {
    var o = paidIntent();
    var req = get("/api/v1/payments/vnpay/return");
    callback(o, "10011").forEach(req::param);
    mvc.perform(req)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.orderId").value(o.id().toString()))
        .andExpect(jsonPath("$.data.status").value("AWAITING_CONFIRMATION"));
    assertEquals(0, periods());
    assertEquals("PENDING_PAYMENT", service.get(subject(), o.id()).status());
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void concurrentIpnOnlyFulfillsOnce() throws Exception {
    var o = paidIntent();
    var data = callback(o, "10012");
    var pool = Executors.newFixedThreadPool(2);
    try {
      var gate = new CountDownLatch(1);
      var jobs = new ArrayList<Future<String>>();
      for (int i = 0; i < 2; i++)
        jobs.add(
            pool.submit(
                () -> {
                  gate.await();
                  return process(data).code();
                }));
      gate.countDown();
      var results =
          List.of(jobs.get(0).get(10, TimeUnit.SECONDS), jobs.get(1).get(10, TimeUnit.SECONDS));
      assertEquals(1, Collections.frequency(results, "00"));
      assertEquals(1, Collections.frequency(results, "02"));
      assertEquals(1, periods());
    } finally {
      pool.shutdownNow();
      cleanup();
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void failureAfterPeriodLinkRollsBackAndRetrySucceeds() throws Exception {
    var o = paidIntent();
    var data = callback(o, "10013");
    var target =
        org.springframework.test.util.AopTestUtils.<VnpayReceiptRepository>getUltimateTargetObject(
            receipts);
    doAnswer(
            call -> {
              call.callRealMethod();
              throw new IllegalStateException("Simulated post-link failure");
            })
        .when(target)
        .link(any(UUID.class), any(UUID.class), any(UUID.class));
    try {
      var req = get("/api/v1/payments/vnpay/ipn");
      data.forEach(req::param);
      mvc.perform(req).andExpect(status().isOk()).andExpect(jsonPath("$.RspCode").value("99"));
      assertEquals(0, periods());
      assertEquals(
          0,
          jdbc.queryForObject(
              "select count(*) from premium_payment_receipts where order_id=?",
              Integer.class,
              o.id()));
      assertEquals(
          0,
          jdbc.queryForObject(
              "select count(*) from premium_provider_transactions where order_id=?",
              Integer.class,
              o.id()));
      assertEquals("PENDING_PAYMENT", service.get(subject(), o.id()).status());
      reset(target);
      assertEquals("00", process(data).code());
      assertEquals(1, periods());
    } finally {
      reset(target);
      cleanup();
    }
  }

  @Test
  void bankTimestampPrecisionDoesNotRejectSameSecondPayment() {
    when(clock.instant()).thenReturn(NOW.plusNanos(123456000));
    var o = paidIntent();
    assertEquals("00", process(callback(o, "10014")).code());
    assertEquals(1, periods());
  }

  @Test
  void secondPaidOrderExtendsExistingSubscription() {
    var first = paidIntent();
    process(callback(first, "10015"));
    var second = paidIntent();
    assertEquals("00", process(callback(second, "10016")).code());
    assertEquals(2, periods());
    assertEquals(
        NOW.plusSeconds(60 * 86400L),
        subscriptions.benefits(subject(), PlanAudience.BUYER).paidEndsAt());
  }

  @Test
  void reversalPreservesPeriodAndRecordsReview() {
    var o = paidIntent();
    var data = callback(o, "10017");
    process(data);
    assertEquals("00", process(change(data, "vnp_TransactionStatus", "04")).code());
    assertEquals(1, periods());
    assertEquals("FULFILLED", service.get(subject(), o.id()).status());
    assertEquals(
        1,
        jdbc.queryForObject(
            "select count(*) from premium_payment_receipts where order_id=? and"
                + " outcome='REVIEW_REQUIRED'",
            Integer.class,
            o.id()));
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void concurrentDifferentUsersCannotShareProviderTransaction() throws Exception {
    var a = paidIntent();
    var b = service.create(other.getId().toString(), UUID.randomUUID(), request());
    service.pay(other.getId().toString(), b.id(), "127.0.0.1");
    var first = callback(a, "10018");
    var second = callback(service.get(other.getId().toString(), b.id()), "00010018");
    var pool = Executors.newFixedThreadPool(2);
    var gate = new CountDownLatch(1);
    try {
      var jobs =
          List.of(
              pool.submit(
                  () -> {
                    gate.await();
                    return process(first).code();
                  }),
              pool.submit(
                  () -> {
                    gate.await();
                    return process(second).code();
                  }));
      gate.countDown();
      for (var job : jobs) assertEquals("00", job.get(10, TimeUnit.SECONDS));
      assertEquals(1, periods());
      assertEquals(
          Set.of("FULFILLED", "REVIEW_REQUIRED"),
          Set.of(
              service.get(subject(), a.id()).status(),
              service.get(other.getId().toString(), b.id()).status()));
    } finally {
      pool.shutdownNow();
      cleanup();
    }
  }

  com.lootfast.backend.premium.vnpay.query.QueryResult queried(
      com.lootfast.backend.premium.vnpay.query.QueryJob job, String status, long amount) {
    try {
      var data = new LinkedHashMap<String, String>();
      data.put("vnp_ResponseId", UUID.randomUUID().toString().replace("-", ""));
      data.put("vnp_Command", "querydr");
      data.put("vnp_ResponseCode", "00");
      data.put("vnp_Message", "Query successful");
      data.put("vnp_TmnCode", "TEST0001");
      data.put("vnp_TxnRef", job.reference());
      data.put("vnp_Amount", Long.toString(amount));
      data.put("vnp_BankCode", "NCB");
      data.put("vnp_PayDate", "20300102070000");
      data.put("vnp_TransactionNo", "900123");
      data.put("vnp_TransactionType", "01");
      data.put("vnp_TransactionStatus", status);
      data.put("vnp_OrderInfo", "Query Premium " + job.reference());
      data.put("vnp_PromotionCode", "");
      data.put("vnp_PromotionAmount", "");
      var mac = javax.crypto.Mac.getInstance("HmacSHA512");
      mac.init(
          new javax.crypto.spec.SecretKeySpec(
              "test-only-secret".getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA512"));
      String signature =
          HexFormat.of()
              .formatHex(
                  mac.doFinal(
                      String.join("|", data.values())
                          .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
      data.put("vnp_SecureHash", signature);
      return queryProtocol.response(
          job, new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(data));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void queryRecoversMissingIpnAndAuditsSource() {
    var o = paidIntent();
    assertTrue(queryStore.claim().isEmpty());
    when(clock.instant()).thenReturn(NOW.plusSeconds(1201));
    var job = queryStore.claim().orElseThrow();
    assertEquals("FULFILLED", queryStore.finish(job, queried(job, "00", 4900000)));
    assertEquals(1, periods());
    assertEquals("FULFILLED", service.get(subject(), o.id()).status());
    assertEquals("DONE", queryStore.status(o.id()).state());
    assertEquals(
        1,
        jdbc.queryForObject(
            "select count(*) from premium_payment_receipts where order_id=? and source='QUERYDR'",
            Integer.class,
            o.id()));
    assertEquals("STALE_WORKER", queryStore.finish(job, queried(job, "00", 4900000)));
    assertEquals(1, periods());
  }

  @Test
  void queryApiSuccessDoesNotMeanPaymentSuccess() {
    var o = paidIntent();
    when(clock.instant()).thenReturn(NOW.plusSeconds(1201));
    var job = queryStore.claim().orElseThrow();
    assertEquals("FAILED", queryStore.finish(job, queried(job, "02", 4900000)));
    assertEquals(0, periods());
    assertEquals("FAILED", service.get(subject(), o.id()).status());
  }

  @Test
  void queryWrongAmountNeverGrantsRights() {
    var o = paidIntent();
    when(clock.instant()).thenReturn(NOW.plusSeconds(1201));
    var job = queryStore.claim().orElseThrow();
    assertEquals("REVIEW_REQUIRED", queryStore.finish(job, queried(job, "00", 1)));
    assertEquals("MANUAL_REVIEW", queryStore.status(o.id()).state());
    assertEquals(0, periods());
  }

  @Test
  void transportFailureBacksOffAndStopsAfterLimit() {
    var o = paidIntent();
    when(clock.instant()).thenReturn(NOW.plusSeconds(1201));
    var job = queryStore.claim().orElseThrow();
    assertEquals(
        "TRANSPORT_ERROR",
        queryStore.finish(
            job, com.lootfast.backend.premium.vnpay.query.QueryResult.retry("TRANSPORT_ERROR")));
    assertEquals(NOW.plusSeconds(1501), queryStore.status(o.id()).nextAttemptAt());
    assertTrue(queryStore.claim().isEmpty());
    error(ErrorCode.PAYMENT_RETRY_LATER, () -> queryStore.request(subject(), o.id()));
    jdbc.update("update premium_reconciliation_jobs set attempts=12 where order_id=?", o.id());
    assertTrue(queryStore.claim().isEmpty());
    assertEquals("EXHAUSTED", queryStore.status(o.id()).state());
    assertEquals(0, periods());
  }

  @Test
  void expiredLeaseCannotApplyOldResponse() {
    var o = paidIntent();
    when(clock.instant()).thenReturn(NOW.plusSeconds(1201));
    var old = queryStore.claim().orElseThrow();
    when(clock.instant()).thenReturn(NOW.plusSeconds(1502));
    var fresh = queryStore.claim().orElseThrow();
    assertNotEquals(old.token(), fresh.token());
    assertEquals("STALE_WORKER", queryStore.finish(old, queried(old, "00", 4900000)));
    assertEquals(0, periods());
    assertEquals("FULFILLED", queryStore.finish(fresh, queried(fresh, "00", 4900000)));
    assertEquals(1, periods());
  }

  @Test
  void ownerCanQueueCheckButCannotInspectOtherOrder() {
    var o = paidIntent();
    error(
        ErrorCode.PREMIUM_ORDER_NOT_FOUND,
        () -> queryStore.request(other.getId().toString(), o.id()));
    error(ErrorCode.PAYMENT_RETRY_LATER, () -> queryStore.request(subject(), o.id()));
    when(clock.instant()).thenReturn(NOW.plusSeconds(301));
    assertEquals("WAITING", queryStore.request(subject(), o.id()).state());
    assertEquals(NOW.plusSeconds(301), queryStore.status(o.id()).nextAttemptAt());
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void concurrentQueryAndIpnGrantOnlyOnePeriod() throws Exception {
    var o = paidIntent();
    when(clock.instant()).thenReturn(NOW.plusSeconds(1201));
    var job = queryStore.claim().orElseThrow();
    var response = queried(job, "00", 4900000);
    var ipnData = callback(o, "900123");
    var pool = Executors.newFixedThreadPool(2);
    var gate = new CountDownLatch(1);
    try {
      var a =
          pool.submit(
              () -> {
                gate.await();
                return queryStore.finish(job, response);
              });
      var b =
          pool.submit(
              () -> {
                gate.await();
                return process(ipnData);
              });
      gate.countDown();
      a.get(10, TimeUnit.SECONDS);
      b.get(10, TimeUnit.SECONDS);
      assertEquals(1, periods());
      assertEquals("FULFILLED", service.get(subject(), o.id()).status());
    } finally {
      pool.shutdownNow();
      cleanup();
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void concurrentWorkersClaimJobOnce() throws Exception {
    var o = paidIntent();
    when(clock.instant()).thenReturn(NOW.plusSeconds(1201));
    var pool = Executors.newFixedThreadPool(2);
    var gate = new CountDownLatch(1);
    try {
      var a =
          pool.submit(
              () -> {
                gate.await();
                return queryStore.claim();
              });
      var b =
          pool.submit(
              () -> {
                gate.await();
                return queryStore.claim();
              });
      gate.countDown();
      var first = a.get(10, TimeUnit.SECONDS);
      var second = b.get(10, TimeUnit.SECONDS);
      assertNotEquals(first.isPresent(), second.isPresent());
      assertEquals(1, queryStore.status(o.id()).attempts());
    } finally {
      pool.shutdownNow();
      cleanup();
    }
  }

  @Test
  void reconciliationRoutesRequireAuthenticationAndOwnership() throws Exception {
    var o = paidIntent();
    when(clock.instant()).thenReturn(NOW.plusSeconds(301));
    mvc.perform(post("/api/v1/premium/orders/" + o.id() + "/reconciliation"))
        .andExpect(status().isUnauthorized());
    doNothing().when(querySettings).requireEnabled();
    String buyerToken = tokens.issueAccessToken(buyer).value();
    mvc.perform(
            post("/api/v1/premium/orders/" + o.id() + "/reconciliation")
                .header("Authorization", "Bearer " + buyerToken))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.data.state").value("WAITING"));
    mvc.perform(
            get("/api/v1/premium/orders/" + o.id() + "/reconciliation")
                .header("Authorization", "Bearer " + tokens.issueAccessToken(other).value()))
        .andExpect(status().isNotFound());
    mvc.perform(
            get("/api/v1/premium/orders/" + o.id() + "/reconciliation")
                .header("Authorization", "Bearer " + buyerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.attempts").value(0));
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void queryRollbackDoesNotCompleteJobAndLeaseRetryCanFulfill() {
    var o = paidIntent();
    when(clock.instant()).thenReturn(NOW.plusSeconds(1201));
    var job = queryStore.claim().orElseThrow();
    var target =
        org.springframework.test.util.AopTestUtils.<VnpayReceiptRepository>getUltimateTargetObject(
            receipts);
    doAnswer(
            call -> {
              call.callRealMethod();
              throw new IllegalStateException("Simulated query fulfillment failure");
            })
        .when(target)
        .link(any(UUID.class), any(UUID.class), any(UUID.class));
    try {
      assertThrows(
          RuntimeException.class, () -> queryStore.finish(job, queried(job, "00", 4900000)));
      assertEquals(0, periods());
      assertEquals("PROCESSING", queryStore.status(o.id()).state());
      assertNull(
          jdbc.queryForObject(
              "select completed_at from premium_query_runs where request_id=?",
              java.sql.Timestamp.class,
              job.token()));
      reset(target);
      when(clock.instant()).thenReturn(NOW.plusSeconds(1502));
      var retry = queryStore.claim().orElseThrow();
      assertEquals("FULFILLED", queryStore.finish(retry, queried(retry, "00", 4900000)));
      assertEquals(1, periods());
    } finally {
      reset(target);
      cleanup();
    }
  }

  @Test
  void manualRequestsCannotBypassBackoffOrFloodQueue() {
    var o = paidIntent();
    when(clock.instant()).thenReturn(NOW.plusSeconds(301));
    queryStore.request(subject(), o.id());
    error(ErrorCode.PAYMENT_RETRY_LATER, () -> queryStore.request(subject(), o.id()));
    var first = queryStore.claim().orElseThrow();
    queryStore.finish(
        first, com.lootfast.backend.premium.vnpay.query.QueryResult.retry("TRANSPORT_ERROR"));
    when(clock.instant()).thenReturn(NOW.plusSeconds(602));
    var second = queryStore.claim().orElseThrow();
    queryStore.finish(
        second, com.lootfast.backend.premium.vnpay.query.QueryResult.retry("TRANSPORT_ERROR"));
    when(clock.instant()).thenReturn(NOW.plusSeconds(903));
    error(ErrorCode.PAYMENT_RETRY_LATER, () -> queryStore.request(subject(), o.id()));
    assertEquals(NOW.plusSeconds(1202), queryStore.status(o.id()).nextAttemptAt());
  }

  void cleanup() {
    jdbc.update(
        "delete from premium_order_events where order_id in(select id from premium_orders where"
            + " user_id in (?,?))",
        buyer.getId(),
        other.getId());
    jdbc.update(
        "delete from premium_payment_receipts where order_id in(select id from premium_orders where"
            + " user_id in (?,?))",
        buyer.getId(),
        other.getId());
    jdbc.update(
        "delete from premium_provider_transactions where order_id in(select id from premium_orders"
            + " where user_id in (?,?))",
        buyer.getId(),
        other.getId());
    jdbc.update(
        "delete from premium_entitlements where user_id in (?,?)", buyer.getId(), other.getId());
    jdbc.update(
        "delete from premium_subscription_periods where subscription_id in(select id from"
            + " premium_subscriptions where user_id in (?,?))",
        buyer.getId(),
        other.getId());
    jdbc.update(
        "delete from premium_subscriptions where user_id in (?,?)", buyer.getId(), other.getId());
    jdbc.update(
        "delete from premium_payment_attempts where order_id in(select id from premium_orders where"
            + " user_id in (?,?))",
        buyer.getId(),
        other.getId());
    jdbc.update("delete from premium_orders where user_id in (?,?)", buyer.getId(), other.getId());
    jdbc.update("delete from premium_plan_audits where plan_revision_id=?", plan.id());
    jdbc.update("delete from premium_plan_benefits where plan_revision_id=?", plan.id());
    jdbc.update("delete from premium_plan_revisions where id=?", plan.id());
    jdbc.update("delete from premium_plans where code=?", plan.code());
    jdbc.update("delete from content_legal_versions where id=?", terms.getId());
    jdbc.update(
        "delete from users where id in (?,?,?)", buyer.getId(), other.getId(), admin.getId());
  }
}
