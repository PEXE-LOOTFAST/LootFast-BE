package com.lootfast.backend.support;

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
import com.lootfast.backend.support.dto.TicketResponse;
import com.lootfast.backend.support.entity.TicketCategory;
import com.lootfast.backend.support.entity.TicketOrigin;
import com.lootfast.backend.support.entity.TicketStatus;
import com.lootfast.backend.support.service.SupportTicketService;
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
class SupportTicketServiceTests {
  private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");

  @Autowired UserRepository users;
  @Autowired MerchantProfileRepository merchants;
  @Autowired DealRepository deals;
  @Autowired OrderRepository orders;
  @Autowired SupportTicketService service;

  @MockitoBean Clock clock;
  @MockitoBean ImageStorage imageStorage;

  User buyer;
  MerchantProfile merchant;

  @BeforeEach
  void setup() {
    when(clock.instant()).thenReturn(NOW);
    buyer = user("Buyer", "One", UserRole.BUYER);
    User merchantUser = user("Merchant", "One", UserRole.MERCHANT);
    merchant = new MerchantProfile(merchantUser);
    merchant.updateStoreInformation("Test store", "description", "MEAL", null, null);
    merchant.updateLocation("1 Test", "Ward", "District", "City", BigDecimal.TEN, BigDecimal.TEN);
    merchants.save(merchant);
  }

  @Test
  void createsTicketLinkedToOrder() {
    CustomerOrder order = order(buyer);

    TicketResponse t =
        service.create(
            buyer.getId().toString(),
            TicketCategory.PAYMENT_DISPUTE,
            "Chưa xác nhận chuyển khoản",
            "Tôi đã chuyển nhưng cửa hàng chưa xác nhận",
            order.getPublicId(),
            null,
            null);

    assertEquals(TicketStatus.OPEN, t.status());
    assertEquals(TicketOrigin.BUYER, t.origin());
    assertEquals(TicketCategory.PAYMENT_DISPUTE, t.category());
    assertEquals(order.getPublicId(), t.orderId());
    assertEquals(merchant.getUser().getPublicId(), t.storeId());
    assertNull(t.evidenceImageUrl());
  }

  @Test
  void createsGeneralTicketWithoutOrderOrStore() {
    TicketResponse t =
        service.create(
            buyer.getId().toString(),
            TicketCategory.OTHER,
            "Góp ý",
            "App chạy chậm",
            null,
            null,
            null);

    assertEquals(TicketOrigin.BUYER, t.origin());
    assertNull(t.orderId());
    assertNull(t.storeId());
  }

  @Test
  void merchantCreatorGetsMerchantOrigin() {
    User merchantUser = merchant.getUser();

    TicketResponse t =
        service.create(
            merchantUser.getId().toString(),
            TicketCategory.OTHER,
            "Vấn đề vận hành",
            "Không xem được đơn",
            null,
            null,
            null);

    assertEquals(TicketOrigin.MERCHANT, t.origin());
  }

  @Test
  void cannotLinkAnotherBuyersOrder() {
    CustomerOrder order = order(buyer);
    User intruder = user("Buyer", "Two", UserRole.BUYER);

    ApiException e =
        assertThrows(
            ApiException.class,
            () ->
                service.create(
                    intruder.getId().toString(),
                    TicketCategory.WRONG_DESCRIPTION,
                    "x",
                    "y",
                    order.getPublicId(),
                    null,
                    null));
    assertEquals(ErrorCode.ORDER_NOT_FOUND, e.getErrorCode());
  }

  @Test
  void listShowsOnlyOwnTickets() {
    service.create(buyer.getId().toString(), TicketCategory.OTHER, "a", "a", null, null, null);
    User other = user("Buyer", "Two", UserRole.BUYER);
    service.create(other.getId().toString(), TicketCategory.OTHER, "b", "b", null, null, null);

    PagedResponse<TicketResponse> mine = service.listMine(buyer.getId().toString(), 0, 20);
    assertEquals(1, mine.totalElements());
  }

  @Test
  void getAnotherUsersTicketReturnsNotFound() {
    TicketResponse mine =
        service.create(buyer.getId().toString(), TicketCategory.OTHER, "a", "a", null, null, null);
    User other = user("Buyer", "Two", UserRole.BUYER);

    ApiException e =
        assertThrows(
            ApiException.class, () -> service.get(other.getId().toString(), mine.ticketId()));
    assertEquals(ErrorCode.TICKET_NOT_FOUND, e.getErrorCode());
  }

  @Test
  void storesEvidenceImageWhenProvided() throws Exception {
    when(imageStorage.upload(anyString(), any(), eq(ImageUploadPreset.TICKET_EVIDENCE)))
        .thenReturn(new StoredImage("key/evidence", "https://img.test/t.png"));

    TicketResponse t =
        service.create(
            buyer.getId().toString(),
            TicketCategory.WRONG_DESCRIPTION,
            "Sai mô tả",
            "Món khác ảnh",
            null,
            null,
            pngFile());

    assertEquals("https://img.test/t.png", t.evidenceImageUrl());
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
    return new MockMultipartFile("evidence", "e.png", "image/png", out.toByteArray());
  }

  private User user(String first, String last, UserRole role) {
    return users.save(
        new User(
            first, last, UUID.randomUUID() + "@test.local",
            null, null, role, UserStatus.VERIFIED));
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
