package com.lootfast.backend.content;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.lootfast.backend.auth.service.ConsentService;
import com.lootfast.backend.auth.service.JwtService;
import com.lootfast.backend.common.exception.*;
import com.lootfast.backend.common.storage.*;
import com.lootfast.backend.content.dto.ContentRequests.*;
import com.lootfast.backend.content.entity.*;
import com.lootfast.backend.content.repository.*;
import com.lootfast.backend.content.service.*;
import com.lootfast.backend.user.dto.UpdateDiscoveryPreferencesRequest;
import com.lootfast.backend.user.entity.*;
import com.lootfast.backend.user.repository.*;
import com.lootfast.backend.user.service.CurrentUserService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
abstract class ContentTests {
  @Autowired CategoryService categories;
  @Autowired CategoryRepository categoryRepo;
  @Autowired BannerService banners;
  @Autowired BannerRepository bannerRepo;
  @Autowired BannerImageService images;
  @Autowired LegalContentService legal;
  @Autowired ConsentService consents;
  @Autowired UserConsentRepository consentRepo;
  @Autowired UserRepository users;
  @Autowired CurrentUserService profiles;
  @Autowired JwtService jwt;
  @Autowired MockMvc mvc;
  @MockitoBean ImageStorage storage;
  @MockitoBean StringRedisTemplate redis;
  User admin, buyer;

  @BeforeEach
  void setup() {
    admin = user(UserRole.ADMIN);
    buyer = user(UserRole.BUYER);
  }

  User user(UserRole role) {
    return users.saveAndFlush(
        new User(
            "Content",
            "Test",
            UUID.randomUUID() + "@test.local",
            null,
            "encoded",
            role,
            UserStatus.VERIFIED));
  }

  String subject() {
    return admin.getId().toString();
  }

  String token(User u) {
    return "Bearer " + jwt.issueAccessToken(u).value();
  }

  String code() {
    return "CAT_" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
  }

  void error(ErrorCode code, org.junit.jupiter.api.function.Executable action) {
    assertEquals(code, assertThrows(ApiException.class, action).getErrorCode());
  }

  @Test
  void disabledCategoriesAreHiddenAndPreferencesCannotSelectThemButKeepOldReferences() {
    String code = code();
    var c = categories.create(subject(), new CategoryCreate(code, "Tên cũ", true, 0));
    profiles.updateDiscoveryPreferences(
        buyer.getId().toString(), new UpdateDiscoveryPreferencesRequest(null, List.of(code)));
    var disabled =
        categories.edit(subject(), code, new CategoryEdit("Tên mới", false, 0, c.revision()));
    assertFalse(
        categories.list(false, null, 0, 50).items().stream().anyMatch(x -> x.code().equals(code)));
    assertTrue(
        profiles.getCurrentUser(buyer.getId().toString()).favoriteCategories().contains(code));
    error(
        ErrorCode.CATEGORY_UNAVAILABLE,
        () ->
            profiles.updateDiscoveryPreferences(
                buyer.getId().toString(),
                new UpdateDiscoveryPreferencesRequest(null, List.of(code))));
    error(ErrorCode.CATEGORY_UNAVAILABLE, () -> categories.requireEnabled(code));
    error(ErrorCode.CATEGORY_UNAVAILABLE, () -> categories.requireEnabled("DOES_NOT_EXIST"));
    error(
        ErrorCode.CONTENT_CONFLICT,
        () -> categories.edit(subject(), code, new CategoryEdit("Stale", true, 0, c.revision())));
    categories.edit(subject(), code, new CategoryEdit("Tên mới", true, 0, disabled.revision()));
    categories.requireEnabled(code);
  }

  @Test
  void publicReadAndAdminOnlyWritesHaveConsistentValidationAndOpenApi() throws Exception {
    mvc.perform(get("/api/v1/categories"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items").isArray());
    mvc.perform(get("/api/v1/banners")).andExpect(status().isOk());
    mvc.perform(get("/api/v1/admin/categories")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/admin/categories").header("Authorization", token(buyer)))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/admin/categories")
                .header("Authorization", token(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"code\":\"NEW_API\",\"name\":\"Món mới\",\"enabled\":true,\"sortOrder\":0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.code").value("NEW_API"));
    mvc.perform(get("/api/v1/categories?size=51")).andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/v1/admin/legal-documents")
                .header("Authorization", token(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"document\":\"TERMS_OF_USE\",\"documentVersion\":\"v1\",\"title\":\"\",\"body\":\"\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/admin/banners/{id}/image']").exists())
        .andExpect(jsonPath("$.paths['/api/v1/legal-documents/{document}/current']").exists());
  }

  @Test
  void legalDraftsArePrivateAndPublicationIsImmutableWithHistoricalReads() throws Exception {
    var one =
        legal.create(
            subject(),
            new LegalCreate(ConsentDocument.TERMS_OF_USE, "v1", "Điều khoản", "Nội dung gốc"));
    error(ErrorCode.CONTENT_NOT_FOUND, () -> legal.current(ConsentDocument.TERMS_OF_USE));
    mvc.perform(get("/api/v1/legal-documents/TERMS_OF_USE/versions/v1"))
        .andExpect(status().isNotFound());
    var edited =
        legal.edit(
            subject(), one.id(), new LegalEdit("Điều khoản", "Nội dung đã duyệt", one.revision()));
    var published = legal.publish(subject(), one.id(), edited.revision());
    assertNotNull(published.publishedAt());
    assertEquals("v1", legal.current(ConsentDocument.TERMS_OF_USE).documentVersion());
    error(
        ErrorCode.CONTENT_CONFLICT,
        () ->
            legal.edit(
                subject(), one.id(), new LegalEdit("Changed", "Changed", published.revision())));
    error(
        ErrorCode.CONTENT_CONFLICT,
        () ->
            legal.create(
                subject(),
                new LegalCreate(ConsentDocument.TERMS_OF_USE, "v1", "Duplicate", "Duplicate")));
    mvc.perform(get("/api/v1/admin/legal-documents").header("Authorization", token(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].documentVersion").value("v1"))
        .andExpect(jsonPath("$.data.items[0].body").doesNotExist());
    var two =
        legal.create(
            subject(),
            new LegalCreate(ConsentDocument.TERMS_OF_USE, "v2", "Điều khoản mới", "Bản mới"));
    legal.publish(subject(), two.id(), two.revision());
    assertEquals("v2", legal.current(ConsentDocument.TERMS_OF_USE).documentVersion());
    assertEquals("Nội dung đã duyệt", legal.published(ConsentDocument.TERMS_OF_USE, "v1").body());
  }

  @Test
  void consentVersionsEnforcedOnlyAfterFirstPublicationAndPriorHistoryRetained() {
    consents.recordRequiredConsents(buyer, "legacy-terms", "legacy-privacy");
    assertEquals(
        2,
        consentRepo.findAll().stream()
            .filter(c -> c.getDocumentVersion().startsWith("legacy-"))
            .count());
    var t =
        legal.create(
            subject(), new LegalCreate(ConsentDocument.TERMS_OF_USE, "v1", "Terms", "Body"));
    legal.publish(subject(), t.id(), t.revision());
    error(
        ErrorCode.CONSENT_VERSION_OUTDATED,
        () -> legal.requireCurrentConsent(ConsentDocument.TERMS_OF_USE, "made-up"));
    legal.requireCurrentConsent(ConsentDocument.TERMS_OF_USE, "v1");
    consents.recordRequiredConsents(buyer, "v1", "legacy-privacy-2");
    assertTrue(
        consentRepo.findAll().stream()
            .anyMatch(c -> c.getDocumentVersion().equals("legacy-terms")));
  }

  BannerWrite request(boolean enabled, Instant start, Instant end, long revision) {
    return new BannerWrite("Ưu đãi", "Ảnh ưu đãi", "/deals", enabled, 0, start, end, revision);
  }

  @Test
  void bannersRequireImagesSafeTargetsAndCorrectSchedule() {
    error(
        ErrorCode.VALIDATION_FAILED,
        () -> banners.write(subject(), null, request(true, null, null, 0)));
    error(
        ErrorCode.VALIDATION_FAILED,
        () ->
            banners.write(
                subject(),
                null,
                new BannerWrite("Title", "Alt", "javascript:alert(1)", false, 0, null, null, 0L)));
    error(
        ErrorCode.VALIDATION_FAILED,
        () -> banners.write(subject(), null, request(false, Instant.now(), null, 0)));
    var draft = banners.write(subject(), null, request(false, null, null, 0));
    Banner b = bannerRepo.findById(draft.id()).orElseThrow();
    b.image("https://image.test/banner.png", "key", Instant.now(), admin.getPublicId());
    bannerRepo.flush();
    var now = Instant.now();
    banners.write(
        subject(),
        b.getId(),
        request(true, now.minusSeconds(60), now.plusSeconds(600), b.getRevision()));
    assertTrue(
        banners.list(false, null, 0, 50).items().stream().anyMatch(x -> x.id().equals(b.getId())));
    b.configure(
        "Title",
        "Alt",
        "/deals",
        true,
        0,
        now.minusSeconds(600),
        now.minusSeconds(60),
        now,
        admin.getPublicId());
    bannerRepo.flush();
    assertFalse(
        banners.list(false, null, 0, 50).items().stream().anyMatch(x -> x.id().equals(b.getId())));
  }

  @Test
  void imageUploadValidatesDataAndCleansUpOnConcurrentEdit() throws Exception {
    var b = banners.write(subject(), null, request(false, null, null, 0));
    var out = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", out);
    MultipartFile png = new MockMultipartFile("file", "banner.png", "image/png", out.toByteArray());
    when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(1L);
    when(storage.upload(anyString(), any(byte[].class), eq(ImageUploadPreset.BANNER_IMAGE)))
        .thenReturn(new StoredImage("uploaded-key", "https://image.test/new.png"));
    var uploaded = images.upload(subject(), b.id(), b.revision(), png);
    assertEquals("https://image.test/new.png", uploaded.imageUrl());
    error(ErrorCode.CONTENT_CONFLICT, () -> images.upload(subject(), b.id(), b.revision(), png));
    error(
        ErrorCode.INVALID_BANNER_IMAGE,
        () ->
            images.upload(
                subject(),
                b.id(),
                uploaded.revision(),
                new MockMultipartFile("file", "x.png", "image/png", new byte[] {1, 2, 3})));
    reset(storage);
    when(storage.upload(anyString(), any(byte[].class), eq(ImageUploadPreset.BANNER_IMAGE)))
        .thenAnswer(
            inv -> {
              var current = bannerRepo.findById(b.id()).orElseThrow();
              current.configure(
                  "Concurrent",
                  "Alt",
                  "/deals",
                  false,
                  0,
                  null,
                  null,
                  Instant.now(),
                  admin.getPublicId());
              bannerRepo.flush();
              return new StoredImage("orphan-key", "https://image.test/orphan.png");
            });
    error(
        ErrorCode.CONTENT_CONFLICT,
        () -> images.upload(subject(), b.id(), uploaded.revision(), png));
    verify(storage).delete("orphan-key");
  }

  @Test
  void uploadsRespectSharedQuotaAndProviderFailures() throws Exception {
    var b = banners.write(subject(), null, request(false, null, null, 0));
    var out = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", out);
    var png = new MockMultipartFile("file", "banner.png", "image/png", out.toByteArray());
    when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(11L);
    error(
        ErrorCode.BANNER_IMAGE_RATE_LIMITED,
        () -> images.upload(subject(), b.id(), b.revision(), png));
    verifyNoInteractions(storage);
    when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(1L);
    when(storage.upload(anyString(), any(byte[].class), eq(ImageUploadPreset.BANNER_IMAGE)))
        .thenThrow(new ImageStorageException("Test provider unavailable"));
    error(
        ErrorCode.BANNER_IMAGE_UPLOAD_FAILED,
        () -> images.upload(subject(), b.id(), b.revision(), png));
    assertNull(bannerRepo.findById(b.id()).orElseThrow().getImageUrl());
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void simultaneousCategoryEditsCannotOverwriteEachOther() throws Exception {
    String code = code();
    var created = categories.create(subject(), new CategoryCreate(code, "Initial", true, 0));
    var pool = Executors.newFixedThreadPool(2);
    var gate = new CountDownLatch(1);
    try {
      var tasks = new ArrayList<Future<String>>();
      for (int i = 0; i < 2; i++) {
        final int n = i;
        tasks.add(
            pool.submit(
                () -> {
                  gate.await();
                  try {
                    categories.edit(
                        subject(),
                        code,
                        new CategoryEdit("Edit " + n, true, 0, created.revision()));
                    return "OK";
                  } catch (ApiException e) {
                    return e.getErrorCode().name();
                  }
                }));
      }
      gate.countDown();
      var outcomes = new ArrayList<String>();
      for (var task : tasks) outcomes.add(task.get(10, TimeUnit.SECONDS));
      assertEquals(1, Collections.frequency(outcomes, "OK"));
      assertEquals(1, Collections.frequency(outcomes, "CONTENT_CONFLICT"));
    } finally {
      pool.shutdownNow();
    }
  }
}
