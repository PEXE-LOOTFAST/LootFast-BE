package com.lootfast.backend.admin.report.dto;

import com.lootfast.backend.deal.entity.DealStatus;
import com.lootfast.backend.order.entity.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record SystemReportResponse(
    LocalDate from,
    LocalDate to,
    String timezone,
    Instant asOf,
    Accounts accounts,
    Stores stores,
    Deals deals,
    long placedOrders,
    Map<OrderStatus, Long> ordersByStatus,
    long completedOrders,
    BigDecimal completedTransactionValue,
    long discountedPortions,
    BigDecimal customerSavings,
    List<Daily> daily,
    List<TopStore> topStores) {
  public record Accounts(
      long totalAccounts,
      long buyerAccounts,
      long merchantAccounts,
      long adminAccounts,
      long lockedAccounts,
      long newAccounts,
      long newBuyerAccounts,
      long newMerchantAccounts) {}

  public record Stores(long totalStores, long approvedStores, long pendingApplications) {}

  public record Deals(
      long createdDeals,
      Map<DealStatus, Long> createdByCurrentStatus,
      Map<DealStatus, Long> currentByStatus,
      long openForSaleNow) {}

  public record Daily(
      LocalDate date,
      long placedOrders,
      long completedOrders,
      BigDecimal completedTransactionValue,
      long discountedPortions,
      BigDecimal customerSavings) {}

  public record TopStore(
      UUID storeId,
      String storeName,
      long completedOrders,
      long discountedPortions,
      BigDecimal completedTransactionValue) {}
}
