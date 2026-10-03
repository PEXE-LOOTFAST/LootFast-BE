package com.lootfast.backend.report;

import static org.junit.jupiter.api.Assertions.*;

import com.lootfast.backend.deal.entity.Deal;
import com.lootfast.backend.deal.entity.DealStatus;
import com.lootfast.backend.deal.repository.DealRepository;
import com.lootfast.backend.merchant.entity.MerchantProfile;
import com.lootfast.backend.merchant.repository.MerchantProfileRepository;
import com.lootfast.backend.order.entity.CustomerOrder;
import com.lootfast.backend.order.entity.PaymentMethod;
import com.lootfast.backend.order.repository.OrderRepository;
import com.lootfast.backend.report.dto.StoreReputationResponse;
import com.lootfast.backend.report.entity.ReportType;
import com.lootfast.backend.report.entity.StoreReport;
import com.lootfast.backend.report.repository.StoreReportRepository;
import com.lootfast.backend.report.service.ReputationService;
import com.lootfast.backend.user.entity.User;
import com.lootfast.backend.user.entity.UserRole;
import com.lootfast.backend.user.entity.UserStatus;
import com.lootfast.backend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ReputationServiceTests {
  private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");

  @Autowired UserRepository users;
  @Autowired MerchantProfileRepository merchants;
  @Autowired DealRepository deals;
  @Autowired OrderRepository orders;
  @Autowired StoreReportRepository reports;
  @Autowired ReputationService service;

  User buyer;
  MerchantProfile merchant;
  UUID storeId;

  @BeforeEach
  void setup() {
    buyer =
        users.save(
            new User(
                "Buyer", "One", UUID.randomUUID() + "@test.local",
                null, null, UserRole.BUYER, UserStatus.VERIFIED));
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
  void excludesNoShowAndPenalizesOutOfStock() {
    completed();
    completed();
    completed();
    noShow(); // lỗi khách, KHÔNG vào mẫu số
    outOfStockReport(); // one report -> penalty 5

    StoreReputationResponse r = service.getByStorePublicId(storeId);

    assertEquals(3, r.completedOrders());
    assertEquals(3, r.totalFinalizedOrders()); // no-show không được đếm
    assertEquals(0, BigDecimal.valueOf(1.00).compareTo(r.completionRate()));
    assertEquals(1, r.outOfStockReports());
    assertEquals(95, r.reputationScore()); // 100 - 5
    assertFalse(r.underReview());
  }

  @Test
  void noShowOnlyMeansNotEnoughMerchantData() {
    noShow(); // chỉ có no-show -> không có kết cục thuộc trách nhiệm cửa hàng

    StoreReputationResponse r = service.getByStorePublicId(storeId);

    assertEquals(0, r.completedOrders());
    assertEquals(0, r.totalFinalizedOrders());
    assertNull(r.completionRate());
    assertNull(r.reputationScore());
  }

  private void completed() {
    CustomerOrder o = newOrder();
    o.complete(NOW);
    orders.saveAndFlush(o);
  }

  private void noShow() {
    CustomerOrder o = newOrder();
    o.markNoShow(NOW);
    orders.saveAndFlush(o);
  }

  private void outOfStockReport() {
    CustomerOrder o = orders.saveAndFlush(newOrder());
    reports.saveAndFlush(
        new StoreReport(buyer, merchant, o, ReportType.OUT_OF_STOCK, "hết hàng", NOW));
  }

  private CustomerOrder newOrder() {
    return new CustomerOrder(
        buyer, activeDeal(), 1, PaymentMethod.CASH, UUID.randomUUID(), "fp", NOW);
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
