package com.lootfast.backend.buyer.savedfilter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.lootfast.backend.auth.service.JwtService;
import com.lootfast.backend.buyer.savedfilter.dto.*;
import com.lootfast.backend.buyer.savedfilter.notification.SavedFilterNotificationService;
import com.lootfast.backend.buyer.savedfilter.service.*;
import com.lootfast.backend.common.exception.*;
import com.lootfast.backend.content.dto.ContentRequests.CategoryEdit;
import com.lootfast.backend.content.service.CategoryService;
import com.lootfast.backend.deal.entity.*;
import com.lootfast.backend.deal.repository.DealRepository;
import com.lootfast.backend.merchant.entity.MerchantProfile;
import com.lootfast.backend.merchant.repository.MerchantProfileRepository;
import com.lootfast.backend.notification.entity.*;
import com.lootfast.backend.notification.outbox.*;
import com.lootfast.backend.notification.repository.*;
import com.lootfast.backend.notification.service.MatchingDealNotificationService;
import com.lootfast.backend.favorite.entity.UserFavorite;
import com.lootfast.backend.favorite.repository.UserFavoriteRepository;
import com.lootfast.backend.premium.dto.*;
import com.lootfast.backend.premium.service.PremiumEntitlementService;
import com.lootfast.backend.user.entity.*;
import com.lootfast.backend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(
    properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureMockMvc
@Transactional
abstract class BuyerSavedFilterTests {
  static final Instant NOW = Instant.parse("2030-01-02T09:00:00Z");
  @Autowired SavedFilterService service;
  @Autowired SavedFilterNotificationService alerts;
  @Autowired EmailOutboxStore outboxStore;
  @Autowired NotificationPreferenceRepository preferences;
  @Autowired UserNotificationRepository notifications;
  @Autowired MatchingDealNotificationService basicAlerts;
  @Autowired UserFavoriteRepository favorites;
  @Autowired PlatformTransactionManager tm;
  @MockitoSpyBean EmailOutboxService queuedEmails;
  @Autowired PremiumEntitlementService premium;
  @Autowired UserRepository users;
  @Autowired DealRepository deals;
  @Autowired MerchantProfileRepository merchants;
  @Autowired CategoryService categories;
  @Autowired JdbcTemplate jdbc;
  @Autowired MockMvc mvc;
  @Autowired JwtService tokens;
  @MockitoBean SavedFilterSearchRateLimiter limiter;
  @MockitoBean Clock clock;
  User buyer, admin, merchant;

  @BeforeEach
  void setup() {
    when(clock.instant()).thenReturn(NOW);
    jdbc.update("delete from email_outbox");
    jdbc.update("delete from premium_deal_notification_jobs");
    jdbc.update("delete from premium_entitlements");
    buyer = user(UserRole.BUYER);
    admin = user(UserRole.ADMIN);
    merchant = user(UserRole.MERCHANT);
  }

  User user(UserRole role) {
    return users.saveAndFlush(
        new User(
            "Filter",
            "Test",
            UUID.randomUUID() + "@test.local",
            null,
            null,
            role,
            UserStatus.VERIFIED));
  }

  String subject() {
    return buyer.getId().toString();
  }

  PremiumEntitlement grant(User target, PremiumBenefit benefit, Instant start, Instant end) {
    return premium.grant(
        admin.getId().toString(),
        new GrantPremiumRequest(
            UUID.randomUUID(), target.getPublicId(), start, end, "Trial", benefit));
  }

  PremiumEntitlement grant() {
    return grant(buyer, PremiumBenefit.BUYER_SAVED_FILTERS, NOW, NOW.plusSeconds(86400));
  }

  SavedFilterCriteria criteria() {
    return new SavedFilterCriteria(
        " Gần nhà ",
        BigDecimal.TEN,
        BigDecimal.TEN,
        BigDecimal.ONE,
        null,
        " meal ",
        BigDecimal.valueOf(50000),
        50,
        LocalTime.of(18, 0),
        LocalTime.of(20, 0));
  }

  void error(ErrorCode code, org.junit.jupiter.api.function.Executable action) {
    assertEquals(code, assertThrows(ApiException.class, action).getErrorCode());
  }

  @Test
  void grantsAreBenefitAndRoleSpecificAndExpiryKeepsDataButStopsUse() {
    error(ErrorCode.PREMIUM_REQUIRED, () -> service.create(subject(), criteria()));
    error(
        ErrorCode.ACCESS_DENIED,
        () -> grant(buyer, PremiumBenefit.MERCHANT_ADVANCED_REPORTS, NOW, NOW.plusSeconds(3600)));
    var otherGrant =
        grant(merchant, PremiumBenefit.MERCHANT_ADVANCED_REPORTS, NOW, NOW.plusSeconds(3600));
    assertFalse(service.benefits(subject()).savedFilters());
    var g = grant();
    assertTrue(service.benefits(subject()).savedFilters());
    var filter = service.create(subject(), criteria());
    when(clock.instant()).thenReturn(g.endsAt());
    assertFalse(service.benefits(subject()).savedFilters());
    assertEquals(1, service.list(subject(), 0, 20).totalElements());
    error(ErrorCode.PREMIUM_REQUIRED, () -> service.search(subject(), filter.id(), null, 0, 20));
    error(
        ErrorCode.PREMIUM_REQUIRED,
        () -> service.update(subject(), filter.id(), new UpdateSavedFilterRequest(0L, criteria())));
    verifyNoInteractions(limiter);
    service.delete(subject(), filter.id(), 0);
    assertEquals(0, service.list(subject(), 0, 20).totalElements());
    assertEquals("MERCHANT_ADVANCED_REPORTS", otherGrant.benefit());
  }

  @Test
  void futureAndRevokedGrantsDoNotEnableSavedFiltersAndRetryCannotChangeBenefit() {
    var future =
        grant(buyer, PremiumBenefit.BUYER_SAVED_FILTERS, NOW.plusSeconds(10), NOW.plusSeconds(20));
    assertFalse(service.benefits(subject()).savedFilters());
    when(clock.instant()).thenReturn(future.startsAt());
    assertTrue(service.benefits(subject()).savedFilters());
    premium.revoke(admin.getId().toString(), future.id(), "End trial");
    assertFalse(service.benefits(subject()).savedFilters());
    var request =
        new GrantPremiumRequest(
            future.id(),
            buyer.getPublicId(),
            future.startsAt(),
            future.endsAt(),
            "Trial",
            PremiumBenefit.BUYER_SAVED_FILTERS);
    assertNotNull(premium.grant(admin.getId().toString(), request).revokedAt());
    // Same id remains reserved, even when the target has both roles.
    buyer.addRole(UserRole.MERCHANT);
    users.flush();
    error(
        ErrorCode.PREMIUM_ENTITLEMENT_CONFLICT,
        () ->
            premium.grant(
                admin.getId().toString(),
                new GrantPremiumRequest(
                    future.id(),
                    buyer.getPublicId(),
                    future.startsAt(),
                    future.endsAt(),
                    "Trial")));
  }

  @Test
  void ownershipRevisionsAndDisabledCategoriesAreEnforced() {
    grant();
    var f = service.create(subject(), criteria());
    assertEquals("Gần nhà", f.criteria().name());
    assertEquals("MEAL", f.criteria().categoryCode());
    var stranger = user(UserRole.BUYER);
    grant(stranger, PremiumBenefit.BUYER_SAVED_FILTERS, NOW, NOW.plusSeconds(300));
    error(ErrorCode.SAVED_FILTER_NOT_FOUND, () -> service.get(stranger.getId().toString(), f.id()));
    error(
        ErrorCode.SAVED_FILTER_NOT_FOUND,
        () ->
            service.update(
                stranger.getId().toString(), f.id(), new UpdateSavedFilterRequest(0L, criteria())));
    error(
        ErrorCode.SAVED_FILTER_NOT_FOUND,
        () -> service.delete(stranger.getId().toString(), f.id(), 0));
    assertEquals(
        1,
        service.update(subject(), f.id(), new UpdateSavedFilterRequest(0L, criteria())).revision());
    error(
        ErrorCode.SAVED_FILTER_CONFLICT,
        () -> service.update(subject(), f.id(), new UpdateSavedFilterRequest(0L, criteria())));
    error(ErrorCode.SAVED_FILTER_CONFLICT, () -> service.delete(subject(), f.id(), 0));
    categories.edit(admin.getId().toString(), "MEAL", new CategoryEdit("Meals", false, 0, 0L));
    assertEquals(f.id(), service.get(subject(), f.id()).id());
    error(ErrorCode.CATEGORY_UNAVAILABLE, () -> service.create(subject(), criteria()));
    service.delete(subject(), f.id(), 1);
  }

  @Test
  void validatesInputAndBoundsIncludingDailyPickupTimes() {
    grant();
    var c = criteria();
    error(
        ErrorCode.INVALID_DEAL_FILTER,
        () ->
            service.create(
                subject(),
                new SavedFilterCriteria(
                    c.name(),
                    c.latitude(),
                    c.longitude(),
                    c.radiusKm(),
                    null,
                    null,
                    null,
                    null,
                    LocalTime.of(20, 0),
                    LocalTime.of(18, 0))));
    error(
        ErrorCode.INVALID_DEAL_FILTER,
        () ->
            service.create(
                subject(),
                new SavedFilterCriteria(
                    c.name(),
                    c.latitude(),
                    c.longitude(),
                    c.radiusKm(),
                    null,
                    null,
                    null,
                    null,
                    LocalTime.of(18, 0),
                    null)));
    error(
        ErrorCode.VALIDATION_FAILED,
        () ->
            service.create(
                subject(),
                new SavedFilterCriteria(
                    c.name(),
                    BigDecimal.valueOf(91),
                    c.longitude(),
                    c.radiusKm(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null)));
    error(ErrorCode.VALIDATION_FAILED, () -> service.list(subject(), Integer.MAX_VALUE, 50));
    var f = service.create(subject(), c);
    error(
        ErrorCode.INVALID_DEAL_FILTER,
        () -> service.search(subject(), f.id(), LocalDate.MAX, 0, 20));
    error(ErrorCode.VALIDATION_FAILED, () -> service.search(subject(), f.id(), null, 0, 51));
  }

  MerchantProfile store(User u, BigDecimal lat, BigDecimal lon) {
    var m = new MerchantProfile(u);
    m.updateStoreInformation("Cơm ngon", "Meals", "MEAL", null, null);
    m.updateLocation("Street", "Ward", "District", "City", lat, lon);
    return merchants.saveAndFlush(m);
  }

  Deal deal(MerchantProfile m, String name, BigDecimal price, DealStatus status, int qty) {
    return deals.saveAndFlush(
        new Deal(
            m,
            name,
            "MEAL",
            BigDecimal.valueOf(100000),
            price,
            qty,
            "https://test.local/image.png",
            0,
            NOW.plusSeconds(7200),
            NOW.plusSeconds(10800),
            NOW.minusSeconds(3600),
            NOW.plusSeconds(3600),
            status));
  }

  @Test
  void searchesApplyAllCriteriaPaginationVisibilityAndLiteralKeywords() {
    grant();
    var m = store(merchant, BigDecimal.TEN, BigDecimal.TEN);
    var wanted = deal(m, "Cơm ngon", BigDecimal.valueOf(40000), DealStatus.ACTIVE, 5);
    deal(m, "Too expensive", BigDecimal.valueOf(60000), DealStatus.ACTIVE, 5);
    var hidden = deal(m, "Hidden", BigDecimal.valueOf(40000), DealStatus.ACTIVE, 5);
    hidden.hide(NOW);
    deals.flush();
    var empty = deal(m, "Empty", BigDecimal.valueOf(40000), DealStatus.ACTIVE, 5);
    jdbc.update("update deals set remaining_quantity=0 where id=?", empty.getId());
    deal(
        store(user(UserRole.MERCHANT), BigDecimal.valueOf(11), BigDecimal.TEN),
        "Far",
        BigDecimal.valueOf(40000),
        DealStatus.ACTIVE,
        5);
    var locked = user(UserRole.MERCHANT);
    var lockedStore = store(locked, BigDecimal.TEN, BigDecimal.TEN);
    deal(lockedStore, "Locked", BigDecimal.valueOf(40000), DealStatus.ACTIVE, 5);
    jdbc.update(
        "update users set locked_at=?, lock_reason='Test lock' where id=?",
        java.sql.Timestamp.from(NOW),
        locked.getId());
    var f = service.create(subject(), criteria());
    var result = service.search(subject(), f.id(), null, 0, 1);
    assertEquals(1, result.totalElements());
    assertEquals(wanted.getPublicId(), result.items().getFirst().dealId());
    assertEquals(0, result.items().getFirst().distanceKm().signum());
    assertTrue(service.search(subject(), f.id(), null, 1, 1).items().isEmpty());
    assertTrue(
        service.search(subject(), f.id(), LocalDate.of(2030, 1, 3), 0, 20).items().isEmpty());
    var c = criteria();
    var accents =
        new SavedFilterCriteria(
            c.name(),
            c.latitude(),
            c.longitude(),
            c.radiusKm(),
            "com",
            c.categoryCode(),
            c.maxPrice(),
            c.minDiscountPercent(),
            c.pickupFrom(),
            c.pickupTo());
    f = service.update(subject(), f.id(), new UpdateSavedFilterRequest(0L, accents));
    assertEquals(1, service.search(subject(), f.id(), null, 0, 20).totalElements());
    var literal =
        new SavedFilterCriteria(
            c.name(), c.latitude(), c.longitude(), c.radiusKm(), "%", null, null, null, null, null);
    f = service.update(subject(), f.id(), new UpdateSavedFilterRequest(1L, literal));
    assertTrue(service.search(subject(), f.id(), null, 0, 20).items().isEmpty());
  }

  @Test
  void searchesAcrossDateLineAndRejectsSuspendedStores() {
    grant();
    var m = store(merchant, BigDecimal.ZERO, new BigDecimal("-179.999"));
    var d = deal(m, "Across date line", BigDecimal.valueOf(40000), DealStatus.ACTIVE, 5);
    var c =
        new SavedFilterCriteria(
            "Date line",
            BigDecimal.ZERO,
            new BigDecimal("179.999"),
            BigDecimal.ONE,
            null,
            null,
            null,
            null,
            null,
            null);
    var f = service.create(subject(), c);
    var found = service.search(subject(), f.id(), null, 0, 20);
    assertEquals(1, found.totalElements());
    assertEquals(d.getPublicId(), found.items().getFirst().dealId());
    assertTrue(found.items().getFirst().distanceKm().compareTo(new BigDecimal("0.3")) < 0);
    jdbc.update(
        "update merchant_profiles set suspended_until=? where id=?",
        java.sql.Timestamp.from(NOW.plusSeconds(600)),
        m.getId());
    assertTrue(service.search(subject(), f.id(), null, 0, 20).items().isEmpty());
  }

  @Test
  void apiProtectsRolesAndDocumentsSwaggerContract() throws Exception {
    String path = "/api/v1/buyers/me/saved-filters";
    mvc.perform(get(path)).andExpect(status().isUnauthorized());
    mvc.perform(
            post(path)
                .header("Authorization", "Bearer " + tokens.issueAccessToken(merchant).value())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());
    String token = "Bearer " + tokens.issueAccessToken(buyer).value();
    mvc.perform(get(path).header("Authorization", token)).andExpect(status().isOk());
    mvc.perform(
            post(path)
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Home\",\"latitude\":10,\"longitude\":10,\"radiusKm\":1}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("PREMIUM_REQUIRED"));
    var g = grant();
    String body =
        "{\"id\":\""
            + UUID.randomUUID()
            + "\",\"userId\":\""
            + buyer.getPublicId()
            + "\",\"startsAt\":\""
            + NOW
            + "\",\"endsAt\":\""
            + NOW.plusSeconds(500)
            + "\",\"reason\":\"Trial\",\"benefit\":\"BUYER_SAVED_FILTERS\"}";
    mvc.perform(
            post("/api/v1/admin/premium/entitlements")
                .header("Authorization", "Bearer " + tokens.issueAccessToken(admin).value())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.benefit").value("BUYER_SAVED_FILTERS"));
    mvc.perform(
            post(path)
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Home\",\"latitude\":10,\"longitude\":10,\"radiusKm\":1}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.revision").value(0));
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/buyers/me/saved-filters/{id}/deals']").exists())
        .andExpect(
            jsonPath("$.components.schemas.SavedFilterCriteria.properties.pickupFrom.type")
                .value("string"));
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void concurrentCreatesCannotExceedTenAndConcurrentEditsCannotOverwrite() throws Exception {
    grant();
    for (int i = 0; i < 9; i++) service.create(subject(), criteria());
    var pool = Executors.newFixedThreadPool(2);
    var gate = new CountDownLatch(1);
    try {
      List<Future<String>> results = new ArrayList<>();
      for (int i = 0; i < 2; i++)
        results.add(
            pool.submit(
                () -> {
                  gate.await();
                  try {
                    service.create(subject(), criteria());
                    return "OK";
                  } catch (ApiException e) {
                    return e.getErrorCode().name();
                  }
                }));
      gate.countDown();
      var outcomes = new ArrayList<String>();
      for (var result : results) outcomes.add(result.get(10, TimeUnit.SECONDS));
      assertEquals(1, Collections.frequency(outcomes, "OK"));
      assertEquals(1, Collections.frequency(outcomes, "SAVED_FILTER_LIMIT_REACHED"));
      var f = service.list(subject(), 0, 20).items().getFirst();
      List<Future<String>> edits = new ArrayList<>();
      for (int i = 0; i < 2; i++)
        edits.add(
            pool.submit(
                () -> {
                  try {
                    service.update(subject(), f.id(), new UpdateSavedFilterRequest(0L, criteria()));
                    return "OK";
                  } catch (ApiException e) {
                    return e.getErrorCode().name();
                  }
                }));
      outcomes.clear();
      for (var result : edits) outcomes.add(result.get(10, TimeUnit.SECONDS));
      assertEquals(1, Collections.frequency(outcomes, "OK"));
      assertEquals(1, Collections.frequency(outcomes, "SAVED_FILTER_CONFLICT"));
      assertEquals(10, service.list(subject(), 0, 20).totalElements());
    } finally {
      pool.shutdownNow();
    }
  }

  SavedFilterResponse enabledFilter() {
    var f = service.create(subject(), criteria());
    return service.notifications(
        subject(), f.id(), new FilterNotificationRequest(true, f.revision()));
  }

  void preference(boolean matching, boolean email) {
    var p =
        preferences.findByUserId(buyer.getId()).orElseGet(() -> new NotificationPreference(buyer));
    p.update(matching, true, true, true, email);
    preferences.saveAndFlush(p);
  }

  long matchingCount(Deal d) {
    return jdbc.queryForObject(
        "select count(*) from notifications where recipient_user_id=? and deal_public_id=? and"
            + " type='MATCHING_DEAL'",
        Long.class,
        buyer.getId(),
        d.getPublicId());
  }

  long emailCount(Deal d) {
    return jdbc.queryForObject(
        "select count(*) from email_outbox e join notifications n on n.public_id=e.notification_id"
            + " where n.recipient_user_id=? and n.deal_public_id=?",
        Long.class,
        buyer.getId(),
        d.getPublicId());
  }

  Deal alertDeal() {
    return deal(
        merchants
            .findByUserId(merchant.getId())
            .orElseGet(() -> store(merchant, BigDecimal.TEN, BigDecimal.TEN)),
        "Meal",
        BigDecimal.valueOf(40000),
        DealStatus.ACTIVE,
        5);
  }

  @Test
  void notificationToggleIsOptInOwnedAndCanBeDisabledAfterPremiumExpires() throws Exception {
    var g = grant();
    var f = service.create(subject(), criteria());
    assertFalse(f.notificationsEnabled());
    String path = "/api/v1/buyers/me/saved-filters/" + f.id() + "/notifications";
    mvc.perform(
            put(path)
                .header("Authorization", "Bearer " + tokens.issueAccessToken(buyer).value())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":true,\"expectedRevision\":0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.notificationsEnabled").value(true))
        .andExpect(jsonPath("$.data.revision").value(1));
    error(
        ErrorCode.SAVED_FILTER_CONFLICT,
        () -> service.notifications(subject(), f.id(), new FilterNotificationRequest(false, 0L)));
    var other = user(UserRole.BUYER);
    error(
        ErrorCode.SAVED_FILTER_NOT_FOUND,
        () ->
            service.notifications(
                other.getId().toString(), f.id(), new FilterNotificationRequest(false, 1L)));
    when(clock.instant()).thenReturn(g.endsAt());
    error(
        ErrorCode.PREMIUM_REQUIRED,
        () -> service.notifications(subject(), f.id(), new FilterNotificationRequest(true, 1L)));
    assertFalse(
        service
            .notifications(subject(), f.id(), new FilterNotificationRequest(false, 1L))
            .notificationsEnabled());
  }

  @Test
  void overlappingFiltersProduceOneNotificationAndOneQueuedEmailAndNeverSendInline() {
    grant();
    enabledFilter();
    enabledFilter();
    preference(true, true);
    var d = alertDeal();
    alerts.published(d);
    alerts.published(d);
    assertEquals(0, matchingCount(d));
    assertTrue(alerts.processBatch());
    assertFalse(alerts.processBatch());
    assertEquals(1, matchingCount(d));
    assertEquals(1, emailCount(d));
    var n =
        notifications
            .findByRecipientId(buyer.getId(), org.springframework.data.domain.PageRequest.of(0, 20))
            .getContent()
            .getFirst();
    assertEquals(DealMatchReason.SAVED_FILTER, n.getMatchReason());
    assertEquals(
        "PENDING",
        jdbc.queryForObject(
            "select status from email_outbox where notification_id=?",
            String.class,
            n.getPublicId()));
  }

  @Test
  void basicAndPremiumMatchingShareTheSameDatabaseDeduplication() {
    grant();
    enabledFilter();
    var d = alertDeal();
    favorites.save(UserFavorite.store(buyer, d.getMerchant()));
    basicAlerts.enqueue(d);
    while (basicAlerts.processNextJob()) {}
    assertEquals(1, matchingCount(d));
    alerts.published(d);
    alerts.processBatch();
    assertEquals(1, matchingCount(d));
    assertEquals(0, emailCount(d));
  }

  @Test
  void honorsGlobalPreferencesPremiumExpiryAndDefaultEmailOff() {
    var g = grant();
    enabledFilter();
    preference(false, true);
    var off = alertDeal();
    alerts.published(off);
    alerts.processBatch();
    assertEquals(0, matchingCount(off));
    preference(true, false);
    var inApp = alertDeal();
    alerts.published(inApp);
    alerts.processBatch();
    assertEquals(1, matchingCount(inApp));
    assertEquals(0, emailCount(inApp));
    var expired = alertDeal();
    alerts.published(expired);
    when(clock.instant()).thenReturn(g.endsAt());
    alerts.processBatch();
    assertEquals(0, matchingCount(expired));
  }

  @Test
  void futureOpeningIsScheduledAndEnablingDoesNotBackfillOlderDeals() {
    grant();
    enabledFilter();
    preference(true, true);
    var d = alertDeal();
    jdbc.update(
        "update deals set available_from=? where id=?",
        java.sql.Timestamp.from(NOW.plusSeconds(1800)),
        d.getId());
    // Refresh the entity's sale schedule as real code would read it before publish.
    var sale =
        new Deal(
            store(user(UserRole.MERCHANT), BigDecimal.TEN, BigDecimal.TEN),
            "Future",
            "MEAL",
            BigDecimal.valueOf(100000),
            BigDecimal.valueOf(40000),
            5,
            null,
            0,
            NOW.plusSeconds(7200),
            NOW.plusSeconds(10800),
            NOW.plusSeconds(1800),
            NOW.plusSeconds(3600),
            DealStatus.ACTIVE);
    sale = deals.saveAndFlush(sale);
    alerts.published(sale);
    assertFalse(alerts.processBatch());
    when(clock.instant()).thenReturn(NOW.plusSeconds(1800));
    assertTrue(alerts.processBatch());
    assertEquals(1, matchingCount(sale));
    var old = alertDeal();
    alerts.published(old);
    var f = service.list(subject(), 0, 20).items().getFirst();
    service.notifications(subject(), f.id(), new FilterNotificationRequest(false, f.revision()));
    when(clock.instant()).thenReturn(NOW.plusSeconds(1801));
    service.notifications(subject(), f.id(), new FilterNotificationRequest(true, f.revision() + 1));
    alerts.processBatch();
    assertEquals(0, matchingCount(old));
  }

  @Test
  void emailClaimRechecksFilterAndPremiumBeforeSending() {
    var g = grant();
    var f = enabledFilter();
    preference(true, true);
    var d = alertDeal();
    alerts.published(d);
    alerts.processBatch();
    service.notifications(subject(), f.id(), new FilterNotificationRequest(false, f.revision()));
    assertTrue(outboxStore.claim(Duration.ofSeconds(60)).isEmpty());
    assertEquals(
        "CANCELLED",
        jdbc.queryForObject(
            "select e.status from email_outbox e join notifications n on"
                + " n.public_id=e.notification_id where n.deal_public_id=?",
            String.class,
            d.getPublicId()));
    service.notifications(subject(), f.id(), new FilterNotificationRequest(true, f.revision() + 1));
    var second = alertDeal();
    alerts.published(second);
    alerts.processBatch();
    when(clock.instant()).thenReturn(g.endsAt());
    assertTrue(outboxStore.claim(Duration.ofSeconds(60)).isEmpty());
    assertEquals(
        "CANCELLED",
        jdbc.queryForObject(
            "select e.status from email_outbox e join notifications n on"
                + " n.public_id=e.notification_id where n.deal_public_id=?",
            String.class,
            second.getPublicId()));
  }

  @Test
  void nonmatchingCriteriaAndChangedCriteriaDoNotTriggerAlerts() {
    grant();
    var f = enabledFilter();
    var d = alertDeal();
    var c = f.criteria();
    service.update(
        subject(),
        f.id(),
        new UpdateSavedFilterRequest(
            f.revision(),
            new SavedFilterCriteria(
                c.name(),
                c.latitude(),
                c.longitude(),
                c.radiusKm(),
                null,
                null,
                BigDecimal.valueOf(10000),
                null,
                null,
                null)));
    alerts.published(d);
    alerts.processBatch();
    assertEquals(0, matchingCount(d));
    assertTrue(service.get(subject(), f.id()).notificationsEnabled());
  }

  @Test
  void candidateWindowsAdvanceEvenWhenAnEntireWindowDoesNotMatch() {
    grant();
    enabledFilter();
    var d = alertDeal();
    for (int i = 0; i < 101; i++)
      grant(user(UserRole.BUYER), PremiumBenefit.BUYER_SAVED_FILTERS, NOW, NOW.plusSeconds(3600));
    alerts.published(d);
    assertTrue(alerts.processBatch());
    assertTrue(alerts.processBatch());
    assertFalse(alerts.processBatch());
    assertEquals(1, matchingCount(d));
    assertTrue(
        jdbc.queryForObject(
                "select last_user_id from premium_deal_notification_jobs where deal_id=?",
                Long.class,
                d.getId())
            > buyer.getId());
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void failedEmailEnqueueRollsBackNotificationAndCursorThenRetriesWithoutDuplicates() {
    grant();
    enabledFilter();
    preference(true, true);
    var d = alertDeal();
    var tx = new TransactionTemplate(tm);
    tx.executeWithoutResult(s -> alerts.published(d));
    EmailOutboxService emailTarget =
        org.springframework.test.util.AopTestUtils.getUltimateTargetObject(queuedEmails);
    doThrow(new IllegalStateException("Test enqueue failure"))
        .when(emailTarget)
        .enqueueFilterBatch(anyList(), anyString(), anyString());
    assertThrows(SavedFilterNotificationService.BatchFailure.class, alerts::processBatch);
    assertEquals(0, matchingCount(d));
    assertEquals(
        0,
        jdbc.queryForObject(
            "select last_user_id from premium_deal_notification_jobs where deal_id=?",
            Long.class,
            d.getId()));
    doCallRealMethod().when(emailTarget).enqueueFilterBatch(anyList(), anyString(), anyString());
    assertTrue(alerts.processBatch());
    assertEquals(1, matchingCount(d));
    assertEquals(1, emailCount(d));
    assertFalse(alerts.processBatch());
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void concurrentMatchingWorkersCommitOneNotificationAndOneEmail() throws Exception {
    grant();
    enabledFilter();
    preference(true, true);
    var d = alertDeal();
    new TransactionTemplate(tm).executeWithoutResult(s -> alerts.published(d));
    try (var pool = Executors.newFixedThreadPool(2)) {
      var gate = new CountDownLatch(1);
      var a =
          pool.submit(
              () -> {
                gate.await();
                return alerts.processBatch();
              });
      var b =
          pool.submit(
              () -> {
                gate.await();
                return alerts.processBatch();
              });
      gate.countDown();
      assertTrue(a.get(10, TimeUnit.SECONDS) || b.get(10, TimeUnit.SECONDS));
    }
    assertEquals(1, matchingCount(d));
    assertEquals(1, emailCount(d));
  }

  @Test
  void alertsHonorEverySavedCriterionIncludingLiteralKeywords() {
    grant();
    var f = enabledFilter();
    var c = criteria();
    var mismatches =
        List.of(
            new SavedFilterCriteria(
                "Far",
                BigDecimal.valueOf(20),
                c.longitude(),
                c.radiusKm(),
                null,
                null,
                null,
                null,
                null,
                null),
            new SavedFilterCriteria(
                "Category",
                c.latitude(),
                c.longitude(),
                c.radiusKm(),
                null,
                "BAKERY",
                null,
                null,
                null,
                null),
            new SavedFilterCriteria(
                "Keyword",
                c.latitude(),
                c.longitude(),
                c.radiusKm(),
                "banh",
                null,
                null,
                null,
                null,
                null),
            new SavedFilterCriteria(
                "Literal",
                c.latitude(),
                c.longitude(),
                c.radiusKm(),
                "%",
                null,
                null,
                null,
                null,
                null),
            new SavedFilterCriteria(
                "Discount",
                c.latitude(),
                c.longitude(),
                c.radiusKm(),
                null,
                null,
                null,
                70,
                null,
                null),
            new SavedFilterCriteria(
                "Pickup",
                c.latitude(),
                c.longitude(),
                c.radiusKm(),
                null,
                null,
                null,
                null,
                LocalTime.of(20, 0),
                LocalTime.of(21, 0)));
    for (var changed : mismatches) {
      f = service.update(subject(), f.id(), new UpdateSavedFilterRequest(f.revision(), changed));
      var d = alertDeal();
      alerts.published(d);
      alerts.processBatch();
      assertEquals(0, matchingCount(d), changed.name());
    }
    f =
        service.update(
            subject(),
            f.id(),
            new UpdateSavedFilterRequest(
                f.revision(),
                new SavedFilterCriteria(
                    "Accents",
                    c.latitude(),
                    c.longitude(),
                    c.radiusKm(),
                    "cơm",
                    null,
                    null,
                    null,
                    null,
                    null)));
    var positive = alertDeal();
    alerts.published(positive);
    alerts.processBatch();
    assertEquals(1, matchingCount(positive));
  }

  @Test
  void alertDistanceWorksAcrossDateLine() {
    grant();
    var f =
        service.create(
            subject(),
            new SavedFilterCriteria(
                "Date line",
                BigDecimal.ZERO,
                new BigDecimal("179.999"),
                BigDecimal.ONE,
                null,
                null,
                null,
                null,
                null,
                null));
    service.notifications(subject(), f.id(), new FilterNotificationRequest(true, f.revision()));
    var d =
        deal(
            store(merchant, BigDecimal.ZERO, new BigDecimal("-179.999")),
            "Across",
            BigDecimal.valueOf(40000),
            DealStatus.ACTIVE,
            5);
    alerts.published(d);
    alerts.processBatch();
    assertEquals(1, matchingCount(d));
  }

  @Test
  void lockedBuyerAndLockedStoreNeverReceiveMatchingAlerts() {
    grant();
    enabledFilter();
    var buyerLocked = alertDeal();
    jdbc.update(
        "update users set locked_at=?,lock_reason='Test lock' where id=?",
        java.sql.Timestamp.from(NOW),
        buyer.getId());
    alerts.published(buyerLocked);
    alerts.processBatch();
    assertEquals(0, matchingCount(buyerLocked));
    jdbc.update("update users set locked_at=null,lock_reason=null where id=?", buyer.getId());
    var storeLocked = alertDeal();
    jdbc.update(
        "update users set locked_at=?,lock_reason='Test lock' where id=?",
        java.sql.Timestamp.from(NOW),
        merchant.getId());
    alerts.published(storeLocked);
    alerts.processBatch();
    assertEquals(0, matchingCount(storeLocked));
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void disablingFilterSerializesWithAnInflightMatchingBatch() throws Exception {
    grant();
    var f = enabledFilter();
    preference(true, true);
    var d = alertDeal();
    var tx = new TransactionTemplate(tm);
    tx.executeWithoutResult(s -> alerts.published(d));
    try (var pool = Executors.newSingleThreadExecutor()) {
      java.util.concurrent.atomic.AtomicReference<Future<Boolean>> future =
          new java.util.concurrent.atomic.AtomicReference<>();
      tx.executeWithoutResult(
          s -> {
            jdbc.queryForObject(
                "select id from users where id=? for update", Long.class, buyer.getId());
            service.notifications(
                subject(), f.id(), new FilterNotificationRequest(false, f.revision()));
            future.set(pool.submit(alerts::processBatch));
            assertThrows(
                TimeoutException.class, () -> future.get().get(500, TimeUnit.MILLISECONDS));
          });
      assertTrue(future.get().get(10, TimeUnit.SECONDS));
    }
    assertEquals(0, matchingCount(d));
    assertEquals(0, emailCount(d));
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void failedJobBackoffAllowsOtherDealsToProceed() {
    grant();
    enabledFilter();
    preference(true, true);
    var first = alertDeal();
    var second = alertDeal();
    new TransactionTemplate(tm)
        .executeWithoutResult(
            s -> {
              alerts.published(first);
              alerts.published(second);
            });
    EmailOutboxService target =
        org.springframework.test.util.AopTestUtils.getUltimateTargetObject(queuedEmails);
    doThrow(new IllegalStateException("Test failure"))
        .doCallRealMethod()
        .when(target)
        .enqueueFilterBatch(anyList(), anyString(), anyString());
    var failure =
        assertThrows(SavedFilterNotificationService.BatchFailure.class, alerts::processBatch);
    assertEquals(first.getId().longValue(), failure.dealId());
    alerts.defer(failure);
    assertTrue(alerts.processBatch());
    assertEquals(0, matchingCount(first));
    assertEquals(1, matchingCount(second));
    assertFalse(alerts.processBatch());
    when(clock.instant()).thenReturn(NOW.plusSeconds(30));
    assertTrue(alerts.processBatch());
    assertEquals(1, matchingCount(first));
    assertEquals(
        0,
        jdbc.queryForObject(
            "select failure_count from premium_deal_notification_jobs where deal_id=?",
            Integer.class,
            first.getId()));
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void permanentlyFailingJobStopsAfterFiveAttempts() {
    grant();
    enabledFilter();
    preference(true, true);
    var d = alertDeal();
    new TransactionTemplate(tm).executeWithoutResult(s -> alerts.published(d));
    EmailOutboxService target =
        org.springframework.test.util.AopTestUtils.getUltimateTargetObject(queuedEmails);
    doThrow(new IllegalStateException("Permanent test failure"))
        .when(target)
        .enqueueFilterBatch(anyList(), anyString(), anyString());
    for (int i = 0; i < 5; i++) {
      var failure =
          assertThrows(SavedFilterNotificationService.BatchFailure.class, alerts::processBatch);
      alerts.defer(failure);
      when(clock.instant())
          .thenReturn(
              jdbc.queryForObject(
                      "select next_attempt_at from premium_deal_notification_jobs where deal_id=?",
                      java.sql.Timestamp.class,
                      d.getId())
                  .toInstant());
    }
    assertFalse(alerts.processBatch());
    assertEquals(0, matchingCount(d));
    assertEquals(0, emailCount(d));
    assertNotNull(
        jdbc.queryForObject(
            "select failed_at from premium_deal_notification_jobs where deal_id=?",
            java.sql.Timestamp.class,
            d.getId()));
    assertEquals(
        5,
        jdbc.queryForObject(
            "select failure_count from premium_deal_notification_jobs where deal_id=?",
            Integer.class,
            d.getId()));
  }
}
