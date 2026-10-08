package com.lootfast.backend.review;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.lootfast.backend.auth.service.JwtService;
import com.lootfast.backend.buyer.savedfilter.notification.SavedFilterNotificationService;
import com.lootfast.backend.common.exception.*;
import com.lootfast.backend.deal.entity.*;
import com.lootfast.backend.deal.repository.DealRepository;
import com.lootfast.backend.deal.service.DealResponseMapper;
import com.lootfast.backend.merchant.entity.MerchantProfile;
import com.lootfast.backend.merchant.repository.MerchantProfileRepository;
import com.lootfast.backend.merchant.service.PublicStoreService;
import com.lootfast.backend.order.entity.*;
import com.lootfast.backend.order.repository.OrderRepository;
import com.lootfast.backend.review.dto.ReviewRequests.*;
import com.lootfast.backend.review.service.StoreReviewService;
import com.lootfast.backend.user.entity.*;
import com.lootfast.backend.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate",
    "lootfast.saved-filter-notifications.enabled=true"})
@AutoConfigureMockMvc
abstract class StoreReviewTests {
  static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
  @Autowired UserRepository users;
  @Autowired MerchantProfileRepository merchants;
  @Autowired DealRepository deals;
  @Autowired OrderRepository orders;
  @Autowired StoreReviewService service;
  @Autowired PublicStoreService stores;
  @Autowired DealResponseMapper mapper;
  @Autowired MockMvc mvc;
  @Autowired JwtService tokens;
  @Autowired TestRestTemplate http;
  @Autowired PlatformTransactionManager transactions;
  @MockitoBean SavedFilterNotificationService matchingWorker;
  User buyer, otherBuyer, merchantUser, otherMerchant, admin;
  MerchantProfile merchant;
  Deal deal;

  @BeforeEach
  void setup() {
    buyer = user(UserRole.BUYER); otherBuyer = user(UserRole.BUYER);
    merchantUser = user(UserRole.MERCHANT); otherMerchant = user(UserRole.MERCHANT);
    admin = user(UserRole.ADMIN);
    merchant = new MerchantProfile(merchantUser);
    merchant.updateStoreInformation("Review store", "description", "MEAL", null, null);
    merchant.updateLocation("1 Test", "Ward", "District", "City", BigDecimal.TEN, BigDecimal.TEN);
    merchant = merchants.saveAndFlush(merchant);
    deal = deals.saveAndFlush(new Deal(merchant, "Meal", "MEAL", BigDecimal.valueOf(100000),
        BigDecimal.valueOf(50000), 5, "https://example.test/image.jpg", 10,
        NOW.plusSeconds(3600), NOW.plusSeconds(7200), NOW.minusSeconds(60), NOW.plusSeconds(7200), DealStatus.ACTIVE));
  }
  User user(UserRole role) {
    return users.saveAndFlush(new User("Display", "PrivateSurname", UUID.randomUUID()+"@test.local",
        null, null, role, UserStatus.VERIFIED));
  }
  String subject(User user) { return user.getId().toString(); }
  String auth(User user) { return "Bearer " + tokens.issueAccessToken(user).value(); }
  CustomerOrder order(OrderStatus status) {
    var o = new CustomerOrder(buyer, deal, 1, PaymentMethod.CASH, UUID.randomUUID(), "fp", NOW);
    switch (status) {
      case COMPLETED -> o.complete(NOW);
      case CANCELLED -> o.cancel(NOW);
      case NO_SHOW -> o.markNoShow(NOW);
      default -> { }
    }
    return orders.saveAndFlush(o);
  }
  UUID review() { return service.create(subject(buyer), order(OrderStatus.COMPLETED).getPublicId(), new Create(5, "Tasty")).review().reviewId(); }
  void error(ErrorCode code, Runnable work) { assertEquals(code, assertThrows(ApiException.class, work::run).getErrorCode()); }

  @Test void onlyOwnerOfCompletedOrderCanReviewOnce() {
    for (var status : List.of(OrderStatus.RESERVED, OrderStatus.CANCELLED, OrderStatus.NO_SHOW)) {
      var o = order(status);
      error(ErrorCode.REVIEW_NOT_ALLOWED, () -> service.create(subject(buyer), o.getPublicId(), new Create(5, null)));
    }
    var o = order(OrderStatus.COMPLETED);
    error(ErrorCode.ORDER_NOT_FOUND, () -> service.create(subject(otherBuyer), o.getPublicId(), new Create(5, null)));
    var r = service.create(subject(buyer), o.getPublicId(), new Create(5, "  Tasty  "));
    assertEquals("Tasty", r.review().comment());
    assertEquals(r, service.mine(subject(buyer), o.getPublicId()));
    error(ErrorCode.REVIEW_NOT_FOUND, () -> service.mine(subject(otherBuyer), o.getPublicId()));
    error(ErrorCode.REVIEW_ALREADY_EXISTS, () -> service.create(subject(buyer), o.getPublicId(), new Create(1, null)));
  }

  @Test void merchantCannotReviewOwnStoreUsingBuyerRole() {
    new TransactionTemplate(transactions).executeWithoutResult(tx ->
        users.findById(merchantUser.getId()).orElseThrow().addRole(UserRole.BUYER));
    var selfOrder = new CustomerOrder(merchantUser, deal, 1, PaymentMethod.CASH, UUID.randomUUID(), "fp", NOW);
    selfOrder.complete(NOW);
    orders.saveAndFlush(selfOrder);
    error(ErrorCode.REVIEW_NOT_ALLOWED, () -> service.create(subject(merchantUser), selfOrder.getPublicId(), new Create(5, "Self")));
    assertEquals(0, service.summary(merchant.getId()).reviewCount());
  }

  @Test void merchantReplyIsOwnedImmutableAndReplaySafe() {
    var id = review();
    error(ErrorCode.REVIEW_NOT_FOUND, () -> service.reply(subject(otherMerchant), id, new Reply("Thanks")));
    var first = service.reply(subject(merchantUser), id, new Reply("Thanks"));
    assertEquals(first, service.reply(subject(merchantUser), id, new Reply("Thanks")));
    error(ErrorCode.REVIEW_REPLY_CONFLICT, () -> service.reply(subject(merchantUser), id, new Reply("Changed")));
    assertEquals(1, service.merchantList(subject(merchantUser), 0, 20).totalElements());
    assertEquals(0, service.merchantList(subject(otherMerchant), 0, 20).totalElements());
  }

  @Test void moderationExcludesRatingAndKeepsAuditAndOwnership() {
    var id = review();
    service.create(subject(buyer), order(OrderStatus.COMPLETED).getPublicId(), new Create(3, null));
    assertEquals(0, new BigDecimal("4.00").compareTo(service.summary(merchant.getId()).averageRating()));
    assertEquals(2, stores.getStore(merchantUser.getPublicId(), null, null, 0, 20).rating().reviewCount());
    assertEquals(2, mapper.toDetail(deal, null).rating().reviewCount());
    error(ErrorCode.ACCESS_DENIED, () -> service.visibility(subject(merchantUser), id, new Visibility(true, "Bad")));
    service.visibility(subject(admin), id, new Visibility(true, "Violation"));
    service.visibility(subject(admin), id, new Visibility(true, "Repeat"));
    assertEquals(1, service.history(subject(admin), id, 0, 20).totalElements());
    assertEquals(1, service.publicList(merchantUser.getPublicId(), 0, 20).totalElements());
    assertEquals(0, new BigDecimal("3.00").compareTo(service.summary(merchant.getId()).averageRating()));
    error(ErrorCode.REVIEW_REPLY_CONFLICT, () -> service.reply(subject(merchantUser), id, new Reply("Reply")));
    service.visibility(subject(admin), id, new Visibility(false, "Restored"));
    assertEquals(2, service.history(subject(admin), id, 0, 20).totalElements());
    assertEquals(2, service.summary(merchant.getId()).reviewCount());
    assertTrue(service.adminList(subject(admin), 0, 50).items().stream().anyMatch(r -> id.equals(r.review().reviewId())));
  }

  @Test void concurrentCreationProducesExactlyOneReview() throws Exception {
    var o = order(OrderStatus.COMPLETED);
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      List<Future<String>> results = new ArrayList<>();
      for (int i=0; i<2; i++) results.add(pool.submit(() -> {
        assertTrue(start.await(5, TimeUnit.SECONDS));
        try { service.create(subject(buyer), o.getPublicId(), new Create(4, null)); return "OK"; }
        catch (ApiException e) { return e.getErrorCode().name(); }
      }));
      start.countDown();
      var outcomes = new ArrayList<String>();
      for (var result : results) outcomes.add(result.get(10, TimeUnit.SECONDS));
      assertEquals(1, Collections.frequency(outcomes, "OK"));
      assertEquals(1, Collections.frequency(outcomes, "REVIEW_ALREADY_EXISTS"));
    }
    assertEquals(1, service.summary(merchant.getId()).reviewCount());
  }

  @Test void concurrentRepliesCannotOverwriteEachOther() throws Exception {
    var id = review();
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      List<Future<String>> results = new ArrayList<>();
      for (String text : List.of("First reply", "Second reply")) results.add(pool.submit(() -> {
        assertTrue(start.await(5, TimeUnit.SECONDS));
        try { service.reply(subject(merchantUser), id, new Reply(text)); return "OK"; }
        catch (ApiException e) { return e.getErrorCode().name(); }
      }));
      start.countDown();
      var outcomes = new ArrayList<String>();
      for (var result : results) outcomes.add(result.get(10, TimeUnit.SECONDS));
      assertEquals(1, Collections.frequency(outcomes, "OK"));
      assertEquals(1, Collections.frequency(outcomes, "REVIEW_REPLY_CONFLICT"));
    }
    assertNotNull(service.merchantList(subject(merchantUser), 0, 20).items().getFirst().review().merchantReply());
  }

  @Test void lockedAccountsCannotWriteReviewsOrModerate() {
    var id = review();
    var orderId = order(OrderStatus.COMPLETED).getPublicId();
    buyer.lockAccount("Test", NOW); users.saveAndFlush(buyer);
    admin.lockAccount("Test", NOW); users.saveAndFlush(admin);
    error(ErrorCode.ACCOUNT_LOCKED, () -> service.create(subject(buyer), orderId, new Create(5, null)));
    error(ErrorCode.ACCOUNT_LOCKED, () -> service.visibility(subject(admin), id, new Visibility(true, "Bad")));
  }

  @Test void publicApiProtectsPrivacyAndWriteApisValidateAndAuthorize() throws Exception {
    var id = review();
    mvc.perform(get("/api/v1/stores/{id}/reviews", merchantUser.getPublicId()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].reviewId").value(id.toString()))
        .andExpect(jsonPath("$.data.items[0].orderId").doesNotExist())
        .andExpect(jsonPath("$.data.items[0].email").doesNotExist())
        .andExpect(jsonPath("$.data.items[0].authorName").value("Display"));
    var o = order(OrderStatus.COMPLETED);
    mvc.perform(post("/api/v1/orders/{id}/review", o.getPublicId()).contentType("application/json").content("{\"rating\":5}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(post("/api/v1/orders/{id}/review", o.getPublicId()).header("Authorization", auth(merchantUser))
        .contentType("application/json").content("{\"rating\":5}")) .andExpect(status().isForbidden());
    for (var body : List.of("{\"rating\":0}", "{\"rating\":6}", "{}", "{\"rating\":5,\"comment\":\"" + "a".repeat(1001) + "\"}"))
      mvc.perform(post("/api/v1/orders/{id}/review", o.getPublicId()).header("Authorization", auth(buyer))
          .contentType("application/json").content(body)).andExpect(status().isBadRequest());
    mvc.perform(put("/api/v1/admin/reviews/{id}/visibility", id).header("Authorization", auth(buyer))
        .contentType("application/json").content("{\"hidden\":true,\"reason\":\"Bad\"}")) .andExpect(status().isForbidden());
    mvc.perform(put("/api/v1/admin/reviews/{id}/visibility", id).header("Authorization", auth(admin))
        .contentType("application/json").content("{\"hidden\":true,\"reason\":\" \"}")) .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/stores/{id}/reviews", merchantUser.getPublicId()).param("size", "51")) .andExpect(status().isBadRequest());
  }

  @Test void realPostgresStartupHealthAndOpenApiAreAvailable() {
    var health = http.getForEntity("/actuator/health", JsonNode.class);
    assertTrue(health.getStatusCode().is2xxSuccessful());
    assertEquals("UP", health.getBody().path("status").asText());
    var docs = http.getForEntity("/v3/api-docs", JsonNode.class);
    assertTrue(docs.getStatusCode().is2xxSuccessful());
    assertTrue(docs.getBody().path("paths").has("/api/v1/stores/{merchantId}/reviews"));
    assertTrue(docs.getBody().path("paths").path("/api/v1/orders/{orderId}/review")
        .path("post").path("security").path(0).has("bearerAuth"));
  }
}
