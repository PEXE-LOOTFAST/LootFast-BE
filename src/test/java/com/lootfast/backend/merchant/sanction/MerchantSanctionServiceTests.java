package com.lootfast.backend.merchant.sanction;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.deal.entity.Deal;
import com.lootfast.backend.deal.entity.DealStatus;
import com.lootfast.backend.deal.repository.DealRepository;
import com.lootfast.backend.merchant.entity.MerchantProfile;
import com.lootfast.backend.merchant.repository.MerchantProfileRepository;
import com.lootfast.backend.merchant.sanction.dto.MerchantSanctionResponse;
import com.lootfast.backend.merchant.sanction.entity.SanctionType;
import com.lootfast.backend.merchant.sanction.service.MerchantSanctionService;
import com.lootfast.backend.user.entity.User;
import com.lootfast.backend.user.entity.UserRole;
import com.lootfast.backend.user.entity.UserStatus;
import com.lootfast.backend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class MerchantSanctionServiceTests {
  private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
  private static final long DAY = 24 * 3600;

  @Autowired UserRepository users;
  @Autowired MerchantProfileRepository merchants;
  @Autowired DealRepository deals;
  @Autowired MerchantSanctionService service;

  @MockitoBean Clock clock;

  User admin;
  MerchantProfile merchant;
  UUID storeId;

  @BeforeEach
  void setup() {
    when(clock.instant()).thenReturn(NOW);
    admin =
        users.save(
            new User(
                "Admin", "One", UUID.randomUUID() + "@test.local",
                null, null, UserRole.ADMIN, UserStatus.VERIFIED));
    User merchantUser =
        users.save(
            new User(
                "Merchant", "One", UUID.randomUUID() + "@test.local",
                null, null, UserRole.MERCHANT, UserStatus.VERIFIED));
    merchant = new MerchantProfile(merchantUser);
    merchant.updateStoreInformation("Test store", "description", "MEAL", null, null);
    merchant.updateLocation("1 Test", "Ward", "District", "City", BigDecimal.TEN, BigDecimal.TEN);
    merchants.save(merchant);
    storeId = merchantUser.getPublicId();
  }

  @Test
  void warnRecordsSanctionWithoutSuspending() {
    activeDeal();

    MerchantSanctionResponse resp = service.warn(adminId(), storeId, "Ảnh sai mô tả");

    assertEquals(SanctionType.WARNING, resp.type());
    assertTrue(resp.active());
    assertNull(resp.endsAt());
    assertFalse(reloadMerchant().isSuspended(NOW));
    assertEquals(1, storeDealCount()); // vẫn hiển thị
  }

  @Test
  void suspendSetsDeadlineAndHidesStoreDeals() {
    activeDeal();
    assertEquals(1, storeDealCount());

    MerchantSanctionResponse resp = service.suspend(adminId(), storeId, "Vi phạm nhiều lần", 7);

    assertEquals(SanctionType.SUSPENSION, resp.type());
    assertEquals(NOW.plusSeconds(7 * DAY), resp.endsAt());
    assertTrue(reloadMerchant().isSuspended(NOW));
    assertEquals(0, storeDealCount()); // bị ẩn khỏi feed cửa hàng
  }

  @Test
  void liftRestoresStoreDeals() {
    activeDeal();
    UUID sanctionId = service.suspend(adminId(), storeId, "Vi phạm", 7).sanctionId();
    assertEquals(0, storeDealCount());

    service.lift(adminId(), sanctionId);

    assertFalse(reloadMerchant().isSuspended(NOW));
    assertEquals(1, storeDealCount());
  }

  @Test
  void liftingTwiceFails() {
    UUID sanctionId = service.suspend(adminId(), storeId, "Vi phạm", 7).sanctionId();
    service.lift(adminId(), sanctionId);

    ApiException e =
        assertThrows(ApiException.class, () -> service.lift(adminId(), sanctionId));
    assertEquals(ErrorCode.SANCTION_ALREADY_LIFTED, e.getErrorCode());
  }

  @Test
  void liftingUnknownSanctionFails() {
    ApiException e =
        assertThrows(ApiException.class, () -> service.lift(adminId(), UUID.randomUUID()));
    assertEquals(ErrorCode.MERCHANT_SANCTION_NOT_FOUND, e.getErrorCode());
  }

  @Test
  void expiredSuspensionIsSwept() {
    service.suspend(adminId(), storeId, "Vi phạm", 1); // hết hạn sau 1 ngày
    assertTrue(reloadMerchant().isSuspended(NOW));

    when(clock.instant()).thenReturn(NOW.plusSeconds(2 * DAY));
    int cleared = service.clearExpiredSuspensions();

    assertEquals(1, cleared);
    assertNull(reloadMerchant().getSuspendedUntil());
  }

  private String adminId() {
    return admin.getId().toString();
  }

  private MerchantProfile reloadMerchant() {
    return merchants.findById(merchant.getId()).orElseThrow();
  }

  private long storeDealCount() {
    return deals
        .findPublicStoreDeals(merchant.getId(), DealStatus.ACTIVE, NOW, PageRequest.of(0, 20))
        .getTotalElements();
  }

  private void activeDeal() {
    deals.saveAndFlush(
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
