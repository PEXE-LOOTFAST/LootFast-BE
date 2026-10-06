package com.lootfast.backend.merchant.performance.service;

import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.deal.service.MerchantDealAccess;
import com.lootfast.backend.merchant.repository.MerchantProfileRepository;
import com.lootfast.backend.merchant.performance.dto.MerchantPerformanceResponse;
import com.lootfast.backend.merchant.performance.dto.MerchantPerformanceResponse.DailyPerformance;
import com.lootfast.backend.merchant.performance.repository.MerchantPerformanceRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MerchantPerformanceService {
  private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
  private final MerchantDealAccess access;
  private final MerchantProfileRepository merchants;
  private final MerchantPerformanceRepository reports;
  public MerchantPerformanceService(MerchantDealAccess access, MerchantProfileRepository merchants,
      MerchantPerformanceRepository reports) {
    this.access = access; this.merchants = merchants; this.reports = reports;
  }

  // One PostgreSQL snapshot keeps the overview, daily series and ranking consistent.
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 10)
  public MerchantPerformanceResponse get(String subject, LocalDate from, LocalDate to) {
    if (from == null || to == null || from.isAfter(to)
        || ChronoUnit.DAYS.between(from, to) >= 366
        || from.getYear() < 1 || to.getYear() > 9999) {
      throw new ApiException(ErrorCode.INVALID_REPORT_RANGE,
          "from/to must be valid dates with an inclusive range of at most 366 days");
    }
    var user = access.requireApprovedMerchant(subject);
    var merchant = merchants.findByUserId(user.getId()).orElseThrow(() ->
        new ApiException(ErrorCode.MERCHANT_PROFILE_NOT_FOUND, "Merchant profile is unavailable"));
    var start = from.atStartOfDay(BUSINESS_ZONE).toInstant();
    var end = to.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant();
    var statuses = reports.placedOrders(merchant.getId(), start, end);
    var rows = new HashMap<LocalDate, DailyPerformance>();
    reports.daily(merchant.getId(), start, end).forEach(row -> rows.put(row.date(), row));
    var daily = new ArrayList<DailyPerformance>();
    var revenue = BigDecimal.ZERO;
    var savings = BigDecimal.ZERO;
    long portions = 0;
    for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
      var row = rows.getOrDefault(date, new DailyPerformance(date, BigDecimal.ZERO, 0, BigDecimal.ZERO));
      daily.add(row);
      revenue = revenue.add(row.completedRevenue());
      savings = savings.add(row.customerSavings());
      portions += row.discountedPortions();
    }
    return new MerchantPerformanceResponse(from, to, BUSINESS_ZONE.getId(),
        statuses.values().stream().mapToLong(Long::longValue).sum(), statuses,
        revenue, portions, savings, daily, reports.topDeals(merchant.getId(), start, end));
  }
}
