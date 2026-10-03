package com.lootfast.backend.report;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.common.response.PagedResponse;
import com.lootfast.backend.common.storage.ImageStorage;
import com.lootfast.backend.common.storage.ImageUploadPreset;
import com.lootfast.backend.common.storage.StoredImage;
import com.lootfast.backend.deal.entity.Deal;
import com.lootfast.backend.deal.entity.DealStatus;
import com.lootfast.backend.deal.repository.DealRepository;
import com.lootfast.backend.merchant.entity.MerchantProfile;
import com.lootfast.backend.merchant.repository.MerchantProfileRepository;
import com.lootfast.backend.order.entity.CustomerOrder;
import com.lootfast.backend.order.entity.PaymentMethod;
import com.lootfast.backend.order.repository.OrderRepository;
import com.lootfast.backend.report.dto.StoreReportResponse;
import com.lootfast.backend.report.entity.ReportStatus;
import com.lootfast.backend.report.entity.ReportType;
import com.lootfast.backend.report.service.StoreReportService;
import com.lootfast.backend.user.entity.User;
import com.lootfast.backend.user.entity.UserRole;
import com.lootfast.backend.user.entity.UserStatus;
import com.lootfast.backend.user.repository.UserRepository;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class StoreReportServiceTests {
  private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");

  @Autowired UserRepository users;
  @Autowired MerchantProfileRepository merchants;
  @Autowired DealRepository deals;
  @Autowired OrderRepository orders;
  @Autowired StoreReportService service;

  @MockitoBean Clock clock;
  @MockitoBean ImageStorage imageStorage;

  User buyer;
  MerchantProfile merchant;

  @BeforeEach
  void setup() {
    when(clock.instant()).thenReturn(NOW);
    buyer = buyer("Buyer", "One");
    User merchantUser =
        users.save(
            new User(
                "Merchant", "One", UUID.randomUUID() + "@test.local",
                null, null, UserRole.MERCHANT, UserStatus.VERIFIED));
    merchant = new MerchantProfile(merchantUser);
    merchant.updateStoreInformation("Test store", "description", "MEAL", null, null);
    merchant.updateLocation("1 Test", "Ward", "District", "City", BigDecimal.TEN, BigDecimal.TEN);
    merchants.save(merchant);
  }

  @Test
  void createsReportTiedToOrderAndStore() {
    CustomerOrder order = order(buyer);

    StoreReportResponse response =
        service.create(
            buyer.getId().toString(),
            order.getPublicId(),
            ReportType.OUT_OF_STOCK,
            "Đến nơi báo hết hàng",
            null);

    assertEquals(ReportType.OUT_OF_STOCK, response.reportType());
    assertEquals(ReportStatus.OPEN, response.status());
    assertEquals(order.getPublicId(), response.orderId());
    assertEquals(merchant.getUser().getPublicId(), response.storeId());
    assertNull(response.evidenceImageUrl());
  }

  @Test
  void rejectsDuplicateSameTypeForSameOrder() {
    CustomerOrder order = order(buyer);
    service.create(
        buyer.getId().toString(), order.getPublicId(), ReportType.OUT_OF_STOCK, "lần 1", null);

    ApiException e =
        assertThrows(
            ApiException.class,
            () ->
                service.create(
                    buyer.getId().toString(),
                    order.getPublicId(),
                    ReportType.OUT_OF_STOCK,
                    "lần 2",
                    null));
    assertEquals(ErrorCode.REPORT_ALREADY_EXISTS, e.getErrorCode());
  }

  @Test
  void cannotReportAnotherBuyersOrder() {
    CustomerOrder order = order(buyer);
    User intruder = buyer("Buyer", "Two");

    ApiException e =
        assertThrows(
            ApiException.class,
            () ->
                service.create(
                    intruder.getId().toString(),
                    order.getPublicId(),
                    ReportType.WRONG_DESCRIPTION,
                    "không phải đơn của tôi",
                    null));
    assertEquals(ErrorCode.ORDER_NOT_FOUND, e.getErrorCode());
  }

  @Test
  void listShowsOnlyOwnReports() {
    service.create(
        buyer.getId().toString(), order(buyer).getPublicId(), ReportType.CLOSED_EARLY, "a", null);
    User other = buyer("Buyer", "Two");
    service.create(
        other.getId().toString(), order(other).getPublicId(), ReportType.OTHER, "b", null);

    PagedResponse<StoreReportResponse> mine = service.listMine(buyer.getId().toString(), 0, 20);
    assertEquals(1, mine.totalElements());
    assertEquals(ReportType.CLOSED_EARLY, mine.items().get(0).reportType());
  }

  @Test
  void storesEvidenceImageWhenProvided() throws Exception {
    when(imageStorage.upload(anyString(), any(), eq(ImageUploadPreset.STORE_REPORT_EVIDENCE)))
        .thenReturn(new StoredImage("key/evidence", "https://img.test/evidence.png"));
    CustomerOrder order = order(buyer);

    StoreReportResponse response =
        service.create(
            buyer.getId().toString(),
            order.getPublicId(),
            ReportType.WRONG_DESCRIPTION,
            "ảnh kèm",
            pngFile());

    assertEquals("https://img.test/evidence.png", response.evidenceImageUrl());
  }

  @Test
  void flagsMerchantForReviewAfterThreeOutOfStockReports() {
    service.create(
        buyer.getId().toString(), order(buyer).getPublicId(), ReportType.OUT_OF_STOCK, "1", null);
    service.create(
        buyer.getId().toString(), order(buyer).getPublicId(), ReportType.OUT_OF_STOCK, "2", null);
    assertFalse(merchants.findById(merchant.getId()).orElseThrow().isFlaggedForReview());

    service.create(
        buyer.getId().toString(), order(buyer).getPublicId(), ReportType.OUT_OF_STOCK, "3", null);
    assertTrue(merchants.findById(merchant.getId()).orElseThrow().isFlaggedForReview());
  }

  private CustomerOrder order(User owner) {
    return orders.saveAndFlush(
        new CustomerOrder(
            owner, activeDeal(), 1, PaymentMethod.CASH, UUID.randomUUID(), "fp", NOW));
  }

  private MockMultipartFile pngFile() throws Exception {
    BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, "png", out);
    return new MockMultipartFile("evidence", "evidence.png", "image/png", out.toByteArray());
  }

  private User buyer(String first, String last) {
    return users.save(
        new User(
            first, last, UUID.randomUUID() + "@test.local",
            null, null, UserRole.BUYER, UserStatus.VERIFIED));
  }

  private Deal activeDeal() {
    return deals.saveAndFlush(
        new Deal(
            merchant,
            "Meal",
            "MEAL",
            BigDecimal.valueOf(100_000),
            BigDecimal.valueOf(50_000),
            5,
            "https://example.test/image.jpg",
            10,
            NOW.plusSeconds(3600),
            NOW.plusSeconds(7200),
            NOW.minusSeconds(60),
            NOW.plusSeconds(7200),
            DealStatus.ACTIVE));
  }
}
