package com.lootfast.backend.admin.report;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.lootfast.backend.admin.report.repository.SystemReportRepository;
import com.lootfast.backend.admin.report.service.*;
import com.lootfast.backend.auth.service.JwtService;
import com.lootfast.backend.common.exception.*;
import com.lootfast.backend.deal.entity.*;
import com.lootfast.backend.deal.repository.DealRepository;
import com.lootfast.backend.merchant.application.entity.MerchantApplication;
import com.lootfast.backend.merchant.application.repository.MerchantApplicationRepository;
import com.lootfast.backend.merchant.entity.MerchantProfile;
import com.lootfast.backend.merchant.repository.MerchantProfileRepository;
import com.lootfast.backend.order.entity.*;
import com.lootfast.backend.order.repository.OrderRepository;
import com.lootfast.backend.user.entity.*;
import com.lootfast.backend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.sql.Timestamp;
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
    properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureMockMvc
@Transactional
abstract class SystemReportTests {
  static final LocalDate DAY = LocalDate.of(2030, 1, 2);
  static final Instant START = Instant.parse("2030-01-01T17:00:00Z");
  @Autowired UserRepository users;
  @Autowired MerchantProfileRepository merchants;
  @Autowired MerchantApplicationRepository applications;
  @Autowired DealRepository deals;
  @Autowired OrderRepository orders;
  @Autowired SystemReportService service;
  @Autowired JdbcTemplate jdbc;
  @Autowired MockMvc mvc;
  @Autowired JwtService tokens;
  @MockitoBean Clock clock;
  @MockitoBean SystemReportRateLimiter limiter;
  @MockitoSpyBean SystemReportRepository reports;
  User admin, buyer, owner;
  MerchantProfile store;
  Deal deal;

  @BeforeEach
  void setup() {
    when(clock.instant()).thenReturn(START.plusSeconds(10));
    admin = user(UserRole.ADMIN);
    buyer = user(UserRole.BUYER);
    owner = user(UserRole.MERCHANT);
    store = store(owner);
    deal = deal(store, "Meal");
  }

  String subject() {
    return admin.getId().toString();
  }

  @Test
  void aggregatesAllStoresAndUsesOrderSnapshotsAndCompletionDate() {
    order(deal, 2, START.minusSeconds(3600), START, OrderStatus.COMPLETED, true);
    order(deal, 1, START, START.plusSeconds(5), OrderStatus.COMPLETED, false);
    for (var s :
        List.of(
            OrderStatus.CANCELLED,
            OrderStatus.MERCHANT_CANCELLED,
            OrderStatus.NO_SHOW,
            OrderStatus.RESERVED)) order(deal, 1, START, START, s, true);
    var other = deal(store(user(UserRole.MERCHANT)), "Other");
    order(other, 3, START, START, OrderStatus.COMPLETED, true);
    jdbc.update(
        "update deals set original_price=120000, discounted_price=60000 where id=?", deal.getId());
    var r = service.get(subject(), DAY, DAY);
    assertEquals(6, r.placedOrders());
    assertEquals(2, r.ordersByStatus().get(OrderStatus.COMPLETED));
    assertEquals(3, r.completedOrders());
    assertEquals(6, r.discountedPortions());
    money(250000, r.completedTransactionValue());
    money(300000, r.customerSavings());
    assertEquals(2, r.topStores().size());
    assertEquals(other.getMerchant().getUser().getPublicId(), r.topStores().getFirst().storeId());
    money(150000, r.topStores().getFirst().completedTransactionValue());
    assertEquals(
        2, r.topStores().get(1).completedOrders()); // Unpaid completed still counts as delivered.
    owner.lockAccount("review", START);
    store.suspendUntil(START.plusSeconds(3600));
    users.saveAndFlush(owner);
    merchants.saveAndFlush(store);
    deal.hide(START);
    deals.saveAndFlush(deal);
    money(250000, service.get(subject(), DAY, DAY).completedTransactionValue());
  }

  @Test
  void inclusiveVietnamDaysExcludeOutsideBoundariesAndZeroFill() {
    order(deal, 1, START.minusSeconds(1), START.minusSeconds(1), OrderStatus.COMPLETED, true);
    order(deal, 2, START, START, OrderStatus.COMPLETED, true);
    order(deal, 3, START.plusSeconds(86400), START.plusSeconds(86400), OrderStatus.COMPLETED, true);
    var r = service.get(subject(), DAY, DAY.plusDays(2));
    assertEquals(3, r.daily().size());
    assertEquals(2, r.placedOrders());
    assertEquals(2, r.completedOrders());
    assertEquals(2, r.daily().get(0).discountedPortions());
    assertEquals(3, r.daily().get(1).discountedPortions());
    assertEquals(0, r.daily().get(2).placedOrders());
    money(0, r.daily().get(2).completedTransactionValue());
    var day = service.get(subject(), DAY, DAY);
    assertEquals(1, day.placedOrders());
    assertEquals(2, day.discountedPortions());
  }

  @Test
  void accountRolesOverlapWithoutMultiplyingTotalAndPendingCountsOnlyPending() {
    owner.addRole(UserRole.BUYER);
    users.saveAndFlush(owner);
    owner.lockAccount("review", START);
    users.saveAndFlush(owner);
    jdbc.update(
        "update users set created_at=? where id in (?,?,?)",
        Timestamp.from(START),
        admin.getId(),
        buyer.getId(),
        owner.getId());
    var pendingUser = user(UserRole.BUYER);
    var pending =
        new MerchantApplication(
            pendingUser, "%013d".formatted(pendingUser.getId()), UUID.randomUUID());
    pending.markPending("test-key", "png", START);
    applications.saveAndFlush(pending);
    var r = service.get(subject(), DAY, DAY);
    assertEquals(4, r.accounts().totalAccounts());
    assertEquals(3, r.accounts().buyerAccounts());
    assertEquals(1, r.accounts().merchantAccounts());
    assertEquals(1, r.accounts().lockedAccounts());
    assertEquals(3, r.accounts().newAccounts());
    assertEquals(2, r.accounts().newBuyerAccounts());
    assertEquals(1, r.accounts().newMerchantAccounts());
    assertEquals(1, r.stores().totalStores());
    assertEquals(1, r.stores().approvedStores());
    assertEquals(1, r.stores().pendingApplications());
  }

  @Test
  void currentDealStatesAreDistinctFromCreatedPeriodAndActualOpenSale() {
    jdbc.update(
        "update deals set created_at=? where id=?",
        Timestamp.from(START.minusSeconds(86400)),
        deal.getId());
    var draft = deal(store, "Draft");
    jdbc.update(
        "update deals set status='DRAFT',created_at=? where id=?",
        Timestamp.from(START),
        draft.getId());
    var hidden = deal(store, "Hidden");
    hidden.hide(START);
    deals.saveAndFlush(hidden);
    var removed = deal(store, "Removed");
    removed.remove(admin, "Moderated", START);
    deals.saveAndFlush(removed);
    var sold = deal(store, "Sold");
    sold.reserve(100);
    deals.saveAndFlush(sold);
    var future = deal(store, "Future");
    jdbc.update(
        "update deals set available_from=?,available_until=? where id=?",
        Timestamp.from(START.plusSeconds(60)),
        Timestamp.from(START.plusSeconds(3600)),
        future.getId());
    var r = service.get(subject(), DAY, DAY);
    assertEquals(1, r.deals().createdDeals());
    assertEquals(1, r.deals().createdByCurrentStatus().get(DealStatus.DRAFT));
    assertEquals(2, r.deals().currentByStatus().get(DealStatus.ACTIVE));
    assertEquals(1, r.deals().currentByStatus().get(DealStatus.HIDDEN));
    assertEquals(1, r.deals().currentByStatus().get(DealStatus.REMOVED));
    assertEquals(1, r.deals().currentByStatus().get(DealStatus.SOLD_OUT));
    assertEquals(1, r.deals().openForSaleNow());
    owner.lockAccount("review", START);
    users.saveAndFlush(owner);
    assertEquals(0, service.get(subject(), DAY, DAY).deals().openForSaleNow());
  }

  @Test
  void topStoresAreBoundedAndTieOrderIsStable() {
    for (int i = 0; i < 12; i++)
      order(
          deal(store(user(UserRole.MERCHANT)), "Meal " + i),
          i == 11 ? 5 : 1,
          START,
          START,
          OrderStatus.COMPLETED,
          true);
    var r = service.get(subject(), DAY, DAY);
    assertEquals(10, r.topStores().size());
    money(250000, r.topStores().getFirst().completedTransactionValue());
    var tieIds = r.topStores().subList(1, 10).stream().map(x -> x.storeId().toString()).toList();
    assertEquals(tieIds.stream().sorted().toList(), tieIds);
  }

  @Test
  void validatesRangeAndFreshAdminStateBeforeRateLimit() {
    for (var to : List.of(DAY.minusDays(1), DAY.plusDays(366), LocalDate.of(9999, 12, 31)))
      error(ErrorCode.INVALID_REPORT_RANGE, () -> service.get(subject(), DAY, to));
    error(ErrorCode.ACCESS_DENIED, () -> service.get(buyer.getId().toString(), DAY, DAY));
    error(ErrorCode.ACCESS_DENIED, () -> service.get("invalid", DAY, DAY));
    admin.lockAccount("test", START);
    users.saveAndFlush(admin);
    error(ErrorCode.ACCESS_DENIED, () -> service.get(subject(), DAY, DAY));
    verifyNoInteractions(limiter);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void adminRecheckedAfterRedisWithoutHoldingDatabaseTransaction() {
    doAnswer(
            invocation -> {
              assertFalse(
                  org.springframework.transaction.support.TransactionSynchronizationManager
                      .isActualTransactionActive());
              jdbc.update(
                  "update users set locked_at=?, lock_reason='Test revocation',"
                      + " authentication_version=authentication_version+1 where id=?",
                  Timestamp.from(START),
                  admin.getId());
              return null;
            })
        .when(limiter)
        .requireAllowed(admin.getId());
    try {
      error(ErrorCode.ACCESS_DENIED, () -> service.get(subject(), DAY, DAY));
      verifyNoInteractions(reports);
    } finally {
      jdbc.update("delete from deals where merchant_profile_id=?", store.getId());
      jdbc.update("delete from merchant_applications where user_id=?", owner.getId());
      jdbc.update("delete from merchant_profiles where id=?", store.getId());
      jdbc.update(
          "delete from users where id in (?,?,?)", admin.getId(), buyer.getId(), owner.getId());
    }
  }

  @Test
  void emptyReportAndMaximumRangeStayBounded() {
    var r = service.get(subject(), DAY, DAY.plusDays(365));
    assertEquals(366, r.daily().size());
    assertEquals(0, r.completedOrders());
    assertTrue(r.topStores().isEmpty());
    assertEquals(OrderStatus.values().length, r.ordersByStatus().size());
    money(0, r.completedTransactionValue());
    money(0, r.customerSavings());
    assertEquals(0, r.discountedPortions());
  }

  @Test
  void httpRolesValidationRateLimitAndOpenApiContract() throws Exception {
    String path = "/api/v1/admin/reports/overview";
    mvc.perform(get(path)).andExpect(status().isUnauthorized());
    for (var u : List.of(buyer, owner))
      mvc.perform(
              get(path)
                  .header("Authorization", "Bearer " + tokens.issueAccessToken(u).value())
                  .param("from", "invalid"))
          .andExpect(status().isForbidden());
    var token = "Bearer " + tokens.issueAccessToken(admin).value();
    mvc.perform(
            get(path)
                .header("Authorization", token)
                .param("from", DAY.toString())
                .param("to", DAY.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.timezone").value("Asia/Ho_Chi_Minh"))
        .andExpect(jsonPath("$.data.completedTransactionValue").value(0));
    mvc.perform(get(path).header("Authorization", token).param("from", DAY.toString()))
        .andExpect(status().isBadRequest());
    mvc.perform(
            get(path)
                .header("Authorization", token)
                .param("from", "invalid")
                .param("to", DAY.toString()))
        .andExpect(status().isBadRequest());
    mvc.perform(
            get(path)
                .header("Authorization", token)
                .param("from", DAY.toString())
                .param("to", DAY.minusDays(1).toString()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_REPORT_RANGE"));
    doThrow(new ApiException(ErrorCode.REPORT_RATE_LIMITED))
        .when(limiter)
        .requireAllowed(admin.getId());
    mvc.perform(
            get(path)
                .header("Authorization", token)
                .param("from", DAY.toString())
                .param("to", DAY.toString()))
        .andExpect(status().isTooManyRequests());
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['" + path + "'].get").exists());
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void reportingSnapshotDoesNotMixConcurrentCommittedOrders() throws Exception {
    // setup fixtures are committed in this test; keep them outside the reporting date range.
    var pool = Executors.newSingleThreadExecutor();
    var triggered = new java.util.concurrent.atomic.AtomicBoolean();
    doAnswer(
            invocation -> {
              assertEquals(
                  "repeatable read",
                  jdbc.queryForObject("show transaction_isolation", String.class));
              assertEquals("on", jdbc.queryForObject("show transaction_read_only", String.class));
              var result = invocation.callRealMethod();
              if (triggered.compareAndSet(false, true)) {
                pool.submit(() -> order(deal, 1, START, START, OrderStatus.COMPLETED, true))
                    .get(5, TimeUnit.SECONDS);
              }
              return result;
            })
        .when(reports)
        .accounts(START, START.plusSeconds(86400));
    try {
      var before = service.get(subject(), DAY, DAY);
      assertEquals(0, before.placedOrders());
      assertEquals(0, before.completedOrders());
      assertTrue(before.topStores().isEmpty());
      var after = service.get(subject(), DAY, DAY);
      assertEquals(1, after.placedOrders());
      assertEquals(1, after.completedOrders());
    } finally {
      pool.shutdownNow();
      jdbc.update("delete from customer_orders where buyer_id=?", buyer.getId());
      jdbc.update("delete from deals where merchant_profile_id=?", store.getId());
      jdbc.update("delete from merchant_applications where user_id=?", owner.getId());
      jdbc.update("delete from merchant_profiles where id=?", store.getId());
      jdbc.update(
          "delete from users where id in (?,?,?)", admin.getId(), buyer.getId(), owner.getId());
    }
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

  MerchantProfile store(User u) {
    var m = new MerchantProfile(u);
    m.updateStoreInformation("Report store", "desc", "MEAL", null, null);
    merchants.saveAndFlush(m);
    var a = new MerchantApplication(u, "%013d".formatted(u.getId()), UUID.randomUUID());
    a.approve(admin, START);
    applications.saveAndFlush(a);
    return m;
  }

  Deal deal(MerchantProfile m, String name) {
    return deals.saveAndFlush(
        new Deal(
            m,
            name,
            "MEAL",
            BigDecimal.valueOf(100000),
            BigDecimal.valueOf(50000),
            100,
            "https://test.local/meal.jpg",
            0,
            START.plusSeconds(7200),
            START.plusSeconds(10800),
            START,
            START.plusSeconds(3600),
            DealStatus.ACTIVE));
  }

  void order(
      Deal d, int qty, Instant created, Instant finalized, OrderStatus status, boolean paid) {
    var o =
        new CustomerOrder(buyer, d, qty, PaymentMethod.CASH, UUID.randomUUID(), "test", created);
    if (paid) o.collectCash(d.getMerchant().getUser(), finalized);
    switch (status) {
      case COMPLETED -> o.complete(finalized);
      case CANCELLED, MERCHANT_CANCELLED -> o.cancel(finalized);
      case NO_SHOW -> o.markNoShow(finalized);
      case RESERVED -> {}
    }
    orders.saveAndFlush(o);
    if (status == OrderStatus.MERCHANT_CANCELLED)
      jdbc.update("update customer_orders set status='MERCHANT_CANCELLED' where id=?", o.getId());
  }

  void money(long expected, BigDecimal actual) {
    assertEquals(0, BigDecimal.valueOf(expected).compareTo(actual));
  }

  void error(ErrorCode expected, Runnable r) {
    assertEquals(expected, assertThrows(ApiException.class, r::run).getErrorCode());
  }
}
