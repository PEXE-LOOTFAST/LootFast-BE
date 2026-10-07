package com.lootfast.backend.premium.catalog;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lootfast.backend.auth.service.JwtService;
import com.lootfast.backend.common.exception.*;
import com.lootfast.backend.content.entity.LegalContent;
import com.lootfast.backend.content.repository.LegalContentRepository;
import com.lootfast.backend.premium.catalog.dto.*;
import com.lootfast.backend.premium.catalog.dto.PlanRequests.*;
import com.lootfast.backend.premium.catalog.repository.PlanCatalogRepository;
import com.lootfast.backend.premium.catalog.service.PlanCatalogService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.*;

@SpringBootTest(
    properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureMockMvc
@Transactional
abstract class PlanCatalogTests {
  @Autowired PlanCatalogService service;
  @MockitoSpyBean PlanCatalogRepository repository;
  @Autowired UserRepository users;
  @Autowired LegalContentRepository legal;
  @Autowired JdbcTemplate jdbc;
  @Autowired JwtService tokens;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @MockitoBean Clock clock;
  User admin, buyer, merchant;
  LegalContent terms;
  String code;
  static final Instant NOW = Instant.parse("2030-01-02T00:00:00Z");

  @BeforeEach
  void setup() {
    when(clock.instant()).thenReturn(NOW);
    admin = user(UserRole.ADMIN);
    buyer = user(UserRole.BUYER);
    merchant = user(UserRole.MERCHANT);
    terms = new LegalContent(ConsentDocument.TERMS_OF_USE, UUID.randomUUID().toString());
    terms.edit("Premium terms", "Test only, not production policy", NOW, admin.getPublicId());
    terms.publish(NOW, admin.getPublicId());
    legal.saveAndFlush(terms);
    code = "TEST_" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
  }

  String actor() {
    return admin.getId().toString();
  }

  Configuration config(long price) {
    return new Configuration(
        "Buyer Premium",
        "Saved filters and notifications",
        price,
        30,
        Set.of(PremiumBenefit.BUYER_SAVED_FILTERS),
        terms.getId());
  }

  PlanResponse create() {
    return service.create(actor(), new Create(code, PlanAudience.BUYER, config(49000)));
  }

  @Test
  void draftIsPrivateAndPublishedCatalogSeparatesAudiences() {
    var a = create();
    assertEquals(0, service.list(null, false, null, null, 0, 20).totalElements());
    error(ErrorCode.PREMIUM_PLAN_NOT_FOUND, () -> service.get(null, a.id(), false));
    var published = service.change(actor(), a.id(), new Change(0L, "Launch"), true);
    assertEquals(1, published.version());
    assertEquals(1, published.revision());
    assertEquals("VND", published.currency());
    assertEquals(49000, published.priceVnd());
    assertEquals(terms.getDocumentVersion(), published.termsVersion());
    assertEquals(1, service.list(null, false, PlanAudience.BUYER, null, 0, 20).totalElements());
    assertEquals(0, service.list(null, false, PlanAudience.MERCHANT, null, 0, 20).totalElements());
    var m =
        service.create(
            actor(),
            new Create(
                code + "_M",
                PlanAudience.MERCHANT,
                new Configuration(
                    "Merchant",
                    "Advanced reports",
                    199000L,
                    30,
                    Set.of(PremiumBenefit.MERCHANT_ADVANCED_REPORTS),
                    terms.getId())));
    service.change(actor(), m.id(), new Change(0L, "Launch"), true);
    assertEquals(1, service.list(null, false, PlanAudience.MERCHANT, null, 0, 20).totalElements());
    assertEquals(2, service.list(null, false, null, null, 0, 1).totalElements());
  }

  @Test
  void newPublicationArchivesPreviousButPreservesItsConfigurationAndAudit() {
    var a = create();
    a = service.change(actor(), a.id(), new Change(0L, "Launch"), true);
    final UUID first = a.id();
    error(
        ErrorCode.PREMIUM_PLAN_CONFLICT,
        () -> service.edit(actor(), first, new Edit(1L, config(59000))));
    var b = service.draft(actor(), code, config(59000));
    assertEquals(2, b.version());
    assertEquals(49000, service.get(null, first, false).priceVnd());
    service.change(actor(), b.id(), new Change(0L, "New price"), true);
    assertEquals(59000, service.list(null, false, null, code, 0, 20).items().getFirst().priceVnd());
    var old = service.get(actor(), first, true);
    assertEquals("ARCHIVED", old.status());
    assertEquals(49000, old.priceVnd());
    assertNotNull(old.publishedAt());
    error(ErrorCode.PREMIUM_PLAN_NOT_FOUND, () -> service.get(null, first, false));
    var history = service.history(actor(), code, 0, 50);
    assertEquals(5, history.totalElements());
    assertTrue(
        history.items().stream()
            .anyMatch(
                x ->
                    x.action().equals("AUTO_ARCHIVE")
                        && x.snapshot().priceVnd() == 49000
                        && x.actorId().equals(admin.getPublicId())));
  }

  @Test
  void optimisticRevisionAndDraftUniquenessProtectAdminEdits() {
    var a = create();
    error(
        ErrorCode.PREMIUM_PLAN_CONFLICT,
        () -> service.create(actor(), new Create(code, PlanAudience.BUYER, config(49000))));
    error(ErrorCode.PREMIUM_PLAN_CONFLICT, () -> service.draft(actor(), code, config(59000)));
    var b = service.edit(actor(), a.id(), new Edit(0L, config(59000)));
    assertEquals(1, b.revision());
    error(
        ErrorCode.PREMIUM_PLAN_CONFLICT,
        () -> service.edit(actor(), a.id(), new Edit(0L, config(69000))));
    error(
        ErrorCode.PREMIUM_PLAN_CONFLICT,
        () -> service.change(actor(), a.id(), new Change(0L, "stale"), true));
    assertEquals(59000, service.get(actor(), a.id(), true).priceVnd());
  }

  @Test
  void archiveRemovesFromPublicCatalogWithoutDeletingData() {
    var a = create();
    a = service.change(actor(), a.id(), new Change(0L, "Launch"), true);
    a = service.change(actor(), a.id(), new Change(1L, "Stop sales"), false);
    assertEquals("ARCHIVED", a.status());
    assertEquals(0, service.list(null, false, null, null, 0, 20).totalElements());
    assertEquals(a, service.change(actor(), a.id(), new Change(a.revision(), "Repeat"), false));
    assertEquals(3, service.history(actor(), code, 0, 20).totalElements());
    var b = service.draft(actor(), code, config(49000));
    assertEquals(2, b.version());
    final UUID old = a.id();
    error(
        ErrorCode.PREMIUM_PLAN_CONFLICT,
        () -> service.change(actor(), old, new Change(2L, "Republish old"), true));
  }

  @Test
  void validatesPriceDurationAudienceAndPublishedTerms() {
    for (long price : new long[] {0, -1, 999, 1000000000L})
      error(
          ErrorCode.VALIDATION_FAILED,
          () -> service.create(actor(), new Create(code, PlanAudience.BUYER, config(price))));
    error(
        ErrorCode.VALIDATION_FAILED,
        () -> service.create(actor(), new Create(code, PlanAudience.MERCHANT, config(49000))));
    error(
        ErrorCode.VALIDATION_FAILED,
        () ->
            service.create(
                actor(),
                new Create(
                    code,
                    PlanAudience.BUYER,
                    new Configuration(
                        "Buyer",
                        "desc",
                        49000L,
                        0,
                        Set.of(PremiumBenefit.BUYER_SAVED_FILTERS),
                        terms.getId()))));
    var draftTerms = new LegalContent(ConsentDocument.TERMS_OF_USE, UUID.randomUUID().toString());
    draftTerms.edit("Draft terms", "test", NOW, admin.getPublicId());
    legal.saveAndFlush(draftTerms);
    var c =
        new Configuration(
            "Buyer",
            "desc",
            49000L,
            30,
            Set.of(PremiumBenefit.BUYER_SAVED_FILTERS),
            draftTerms.getId());
    var a = service.create(actor(), new Create(code, PlanAudience.BUYER, c));
    error(
        ErrorCode.VALIDATION_FAILED,
        () -> service.change(actor(), a.id(), new Change(0L, "Launch"), true));
    assertEquals("DRAFT", service.get(actor(), a.id(), true).status());
  }

  @Test
  void httpSecurityValidationPaginationAndOpenApi() throws Exception {
    String path = "/api/v1/admin/premium/plans";
    String token = "Bearer " + tokens.issueAccessToken(admin).value();
    mvc.perform(get(path)).andExpect(status().isUnauthorized());
    for (var u : List.of(buyer, merchant))
      mvc.perform(
              post(path)
                  .header("Authorization", "Bearer " + tokens.issueAccessToken(u).value())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{}"))
          .andExpect(status().isForbidden());
    mvc.perform(
            post(path)
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
    mvc.perform(get(path).header("Authorization", token).param("size", "51"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/premium/plans"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items").isArray());
    var request = new Create(code, PlanAudience.BUYER, config(49000));
    com.fasterxml.jackson.databind.node.ObjectNode fractional = json.valueToTree(request);
    ((com.fasterxml.jackson.databind.node.ObjectNode) fractional.get("configuration"))
        .put("priceVnd", 49000.5);
    mvc.perform(
            post(path)
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(fractional)))
        .andExpect(status().isBadRequest());

    var result =
        mvc.perform(
                post(path)
                    .header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andReturn();
    var id =
        json.readTree(result.getResponse().getContentAsString()).path("data").path("id").asText();
    mvc.perform(get("/api/v1/premium/plans/" + id)).andExpect(status().isNotFound());
    mvc.perform(
            post("/api/v1/admin/premium/plan-revisions/" + id + "/publish")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedRevision\":0,\"reason\":\"Launch\"}"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/premium/plans/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.priceVnd").value(49000));
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/premium/plans'].get").exists());
  }

  @Test
  void freshAdminLockAndPaginationCheckedByService() {
    error(
        ErrorCode.ACCESS_DENIED,
        () ->
            service.create(
                buyer.getId().toString(), new Create(code, PlanAudience.BUYER, config(49000))));
    admin.lockAccount("test", NOW);
    users.saveAndFlush(admin);
    error(
        ErrorCode.ACCESS_DENIED,
        () -> service.create(actor(), new Create(code, PlanAudience.BUYER, config(49000))));
    error(
        ErrorCode.VALIDATION_FAILED,
        () -> service.list(null, false, null, null, Integer.MAX_VALUE, 50));
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void concurrentEditsAndPublishOnlyOneWinner() throws Exception {
    var a = create();
    var pool = Executors.newFixedThreadPool(2);
    try {
      for (boolean publish : new boolean[] {false, true}) {
        var current = service.get(actor(), a.id(), true);
        var gate = new CountDownLatch(1);
        var jobs = new ArrayList<Future<String>>();
        for (int i = 0; i < 2; i++)
          jobs.add(
              pool.submit(
                  () -> {
                    gate.await();
                    try {
                      if (publish)
                        service.change(
                            actor(), a.id(), new Change(current.revision(), "Launch"), true);
                      else
                        service.edit(actor(), a.id(), new Edit(current.revision(), config(59000)));
                      return "OK";
                    } catch (ApiException e) {
                      return e.getErrorCode().name();
                    }
                  }));
        gate.countDown();
        var outcomes = new ArrayList<String>();
        for (var job : jobs) outcomes.add(job.get(10, TimeUnit.SECONDS));
        assertEquals(1, Collections.frequency(outcomes, "OK"));
        assertEquals(1, Collections.frequency(outcomes, "PREMIUM_PLAN_CONFLICT"));
      }
      assertEquals(1, service.list(null, false, null, code, 0, 20).totalElements());
      assertEquals(3, service.history(actor(), code, 0, 20).totalElements());
    } finally {
      pool.shutdownNow();
      jdbc.update("delete from premium_plan_audits where plan_revision_id=?", a.id());
      jdbc.update("delete from premium_plan_benefits where plan_revision_id=?", a.id());
      jdbc.update("delete from premium_plan_revisions where id=?", a.id());
      jdbc.update("delete from premium_plans where code=?", code);
      jdbc.update("delete from content_legal_versions where id=?", terms.getId());
      jdbc.update(
          "delete from users where id in (?,?,?)", admin.getId(), buyer.getId(), merchant.getId());
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void publishAuditFailureRollsBackEntireReplacement() {
    var old = create();
    service.change(actor(), old.id(), new Change(0L, "Launch"), true);
    var next = service.draft(actor(), code, config(59000));
    var target =
        org.springframework.test.util.AopTestUtils.<PlanCatalogRepository>getUltimateTargetObject(
            repository);
    doThrow(new IllegalStateException("Test audit failure"))
        .when(target)
        .audit(
            org.mockito.ArgumentMatchers.any(PlanResponse.class),
            org.mockito.ArgumentMatchers.any(UUID.class),
            org.mockito.ArgumentMatchers.eq("PUBLISH"),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(Instant.class));
    try {
      assertThrows(
          org.springframework.dao.InvalidDataAccessApiUsageException.class,
          () -> service.change(actor(), next.id(), new Change(0L, "Update"), true));
      assertEquals("PUBLISHED", service.get(actor(), old.id(), true).status());
      assertEquals("DRAFT", service.get(actor(), next.id(), true).status());
      assertEquals(old.id(), service.list(null, false, null, code, 0, 20).items().getFirst().id());
      assertEquals(3, service.history(actor(), code, 0, 20).totalElements());
    } finally {
      jdbc.update(
          "delete from premium_plan_audits where plan_revision_id in (?,?)", old.id(), next.id());
      jdbc.update(
          "delete from premium_plan_benefits where plan_revision_id in (?,?)", old.id(), next.id());
      jdbc.update("delete from premium_plan_revisions where id in (?,?)", old.id(), next.id());
      jdbc.update("delete from premium_plans where code=?", code);
      jdbc.update("delete from content_legal_versions where id=?", terms.getId());
      jdbc.update(
          "delete from users where id in (?,?,?)", admin.getId(), buyer.getId(), merchant.getId());
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

  void error(ErrorCode code, Runnable f) {
    assertEquals(code, assertThrows(ApiException.class, f::run).getErrorCode());
  }
}
