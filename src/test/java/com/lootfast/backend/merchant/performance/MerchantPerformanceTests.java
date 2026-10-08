package com.lootfast.backend.merchant.performance;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.lootfast.backend.auth.service.JwtService;
import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.deal.entity.*;
import com.lootfast.backend.deal.repository.DealRepository;
import com.lootfast.backend.merchant.application.entity.MerchantApplication;
import com.lootfast.backend.merchant.application.repository.MerchantApplicationRepository;
import com.lootfast.backend.merchant.entity.MerchantProfile;
import com.lootfast.backend.merchant.repository.MerchantProfileRepository;
import com.lootfast.backend.merchant.performance.service.MerchantPerformanceService;
import com.lootfast.backend.order.entity.*;
import com.lootfast.backend.order.repository.OrderRepository;
import com.lootfast.backend.user.entity.*;
import com.lootfast.backend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureMockMvc
@Transactional
abstract class MerchantPerformanceTests {
  static final LocalDate DAY = LocalDate.of(2030, 1, 2);
  static final Instant START = Instant.parse("2030-01-01T17:00:00Z");
  @Autowired UserRepository users;
  @Autowired MerchantProfileRepository merchants;
  @Autowired MerchantApplicationRepository applications;
  @Autowired DealRepository deals;
  @Autowired OrderRepository orders;
  @Autowired MerchantPerformanceService service;
  @Autowired JdbcTemplate jdbc;
  @Autowired MockMvc mvc;
  @Autowired JwtService tokens;
  User owner, buyer;
  MerchantProfile store;
  Deal deal;

  @BeforeEach void setup() {
    owner = user(UserRole.MERCHANT);
    buyer = user(UserRole.BUYER);
    store = merchant(owner);
    deal = deal(store, "Discount meal");
  }

  @Test void summarizesSnapshotsAndExcludesOtherStoresAndNonCompletedOrders() {
    order(deal, 2, START.minusSeconds(3600), START, OrderStatus.COMPLETED, true);
    order(deal, 1, START, START.plusSeconds(300), OrderStatus.COMPLETED, false);
    order(deal, 1, START, START, OrderStatus.CANCELLED, true);
    order(deal, 1, START, START, OrderStatus.NO_SHOW, false);
    order(deal, 1, START, START, OrderStatus.MERCHANT_CANCELLED, false);
    order(deal, 1, START, START, OrderStatus.RESERVED, false);
    order(deal(merchant(user(UserRole.MERCHANT)), "Other store"), 5, START, START,
        OrderStatus.COMPLETED, true);
    jdbc.update("update deals set original_price=120000, discounted_price=60000 where id=?", deal.getId());
    var result = service.get(owner.getId().toString(), DAY, DAY);
    assertEquals(5, result.placedOrders());
    assertEquals(1, result.ordersByStatus().get(OrderStatus.COMPLETED));
    assertEquals(3, result.discountedPortions());
    money(100000, result.completedRevenue());
    money(150000, result.customerSavings());
    assertEquals(1, result.topDeals().size());
    assertEquals(deal.getPublicId(), result.topDeals().getFirst().dealId());
    assertEquals(2, result.topDeals().getFirst().discountedPortions());
    money(100000, result.topDeals().getFirst().completedRevenue());
  }

  @Test void vietnamDayBoundariesAreInclusiveAndEmptyDaysReturnZeros() {
    order(deal, 1, START.minusSeconds(1), START.minusSeconds(1), OrderStatus.COMPLETED, true);
    order(deal, 2, START, START, OrderStatus.COMPLETED, true);
    order(deal, 3, START.plusSeconds(86400), START.plusSeconds(86400), OrderStatus.COMPLETED, true);
    var result = service.get(owner.getId().toString(), DAY, DAY.plusDays(2));
    assertEquals(3, result.daily().size());
    assertEquals(2, result.daily().get(0).discountedPortions());
    assertEquals(3, result.daily().get(1).discountedPortions());
    assertEquals(0, result.daily().get(2).discountedPortions());
    money(0, result.daily().get(2).completedRevenue());
    assertEquals(5, result.discountedPortions());
    assertEquals(2, result.placedOrders());
  }

  @Test void topDealsAreBoundedAndRankedByRevenue() {
    for (int i=0; i<12; i++) {
      var d = deal(store, "Meal " + i);
      order(d, i == 11 ? 5 : 1, START, START, OrderStatus.COMPLETED, true);
    }
    var result = service.get(owner.getId().toString(), DAY, DAY);
    assertEquals(10, result.topDeals().size());
    assertEquals("Meal 11", result.topDeals().getFirst().name());
    money(250000, result.topDeals().getFirst().completedRevenue());
  }

  @Test void validatesRangeAndApprovalAndReturnsEmptyReport() {
    for (var end : new LocalDate[]{DAY.minusDays(1), DAY.plusDays(366)}) {
      assertEquals(ErrorCode.INVALID_REPORT_RANGE, assertThrows(ApiException.class,
          () -> service.get(owner.getId().toString(), DAY, end)).getErrorCode());
    }
    assertEquals(366, service.get(owner.getId().toString(), DAY, DAY.plusDays(365)).daily().size());
    var result = service.get(owner.getId().toString(), DAY, DAY);
    assertEquals(0, result.placedOrders()); money(0, result.completedRevenue());
    assertTrue(result.topDeals().isEmpty());
    assertEquals(ErrorCode.ACCESS_DENIED, assertThrows(ApiException.class,
        () -> service.get(buyer.getId().toString(), DAY, DAY)).getErrorCode());
    User pending = user(UserRole.MERCHANT);
    assertEquals(ErrorCode.MERCHANT_NOT_APPROVED, assertThrows(ApiException.class,
        () -> service.get(pending.getId().toString(), DAY, DAY)).getErrorCode());
  }

  @Test void apiEnforcesRolesDateValidationAndDocumentsContract() throws Exception {
    String path = "/api/v1/merchants/me/reports/performance";
    mvc.perform(get(path).param("from", DAY.toString()).param("to", DAY.toString()))
        .andExpect(status().isUnauthorized());
    mvc.perform(get(path).header("Authorization", "Bearer " + tokens.issueAccessToken(buyer).value())
        .param("from", DAY.toString()).param("to", DAY.toString())).andExpect(status().isForbidden());
    String token = "Bearer " + tokens.issueAccessToken(owner).value();
    mvc.perform(get(path).header("Authorization", token).param("from", DAY.toString()).param("to", DAY.toString()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.discountedPortions").value(0))
        .andExpect(jsonPath("$.data.timezone").value("Asia/Ho_Chi_Minh"));
    mvc.perform(get(path).header("Authorization", token).param("from", "invalid").param("to", DAY.toString()))
        .andExpect(status().isBadRequest());
    mvc.perform(get(path).header("Authorization", token).param("from", DAY.toString()))
        .andExpect(status().isBadRequest());
    mvc.perform(get(path).header("Authorization", token).param("from", DAY.toString()).param("to", DAY.minusDays(1).toString()))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REPORT_RANGE"));
    mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['" + path + "'].get").exists());
  }

  User user(UserRole role) {
    return users.saveAndFlush(new User("Test", "Account", UUID.randomUUID()+"@test.local",
        null, null, role, UserStatus.VERIFIED));
  }
  MerchantProfile merchant(User owner) {
    var m = new MerchantProfile(owner);
    m.updateStoreInformation("Report store", "description", "MEAL", null, null);
    m.updateLocation("Test street", "Ward", "District", "City", BigDecimal.TEN, BigDecimal.TEN);
    merchants.saveAndFlush(m);
    var app = new MerchantApplication(owner, "%013d".formatted(owner.getId()), UUID.randomUUID());
    app.approve(owner, Instant.now()); applications.saveAndFlush(app);
    return m;
  }
  Deal deal(MerchantProfile merchant, String name) {
    return deals.saveAndFlush(new Deal(merchant, name, "MEAL", BigDecimal.valueOf(100000),
        BigDecimal.valueOf(50000), 100, "https://test.local/meal.jpg", 0,
        START.plusSeconds(7200), START.plusSeconds(10800), START, START.plusSeconds(3600), DealStatus.ACTIVE));
  }
  void order(Deal d, int qty, Instant created, Instant finalized, OrderStatus status, boolean paid) {
    var o = new CustomerOrder(buyer, d, qty, PaymentMethod.CASH, UUID.randomUUID(), "test", created);
    if (paid) o.collectCash(d.getMerchant().getUser(), finalized);
    switch (status) {
      case COMPLETED -> o.complete(finalized);
      case CANCELLED -> o.cancel(finalized);
      case NO_SHOW -> o.markNoShow(finalized);
      case MERCHANT_CANCELLED -> o.cancel(finalized);
      case RESERVED -> { }
    }
    orders.saveAndFlush(o);
    // Exercise the persisted merchant-cancelled state without adding a production transition API.
    if (status == OrderStatus.MERCHANT_CANCELLED) {
      jdbc.update("update customer_orders set status='MERCHANT_CANCELLED' where id=?", o.getId());
    }
  }
  void money(long expected, BigDecimal actual) { assertEquals(0, BigDecimal.valueOf(expected).compareTo(actual)); }
}
