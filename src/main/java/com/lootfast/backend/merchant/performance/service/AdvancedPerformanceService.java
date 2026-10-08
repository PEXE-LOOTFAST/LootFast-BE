package com.lootfast.backend.merchant.performance.service;

import com.lootfast.backend.common.exception.*;
import com.lootfast.backend.merchant.performance.dto.AdvancedPerformanceResponse;
import com.lootfast.backend.merchant.performance.dto.AdvancedPerformanceResponse.*;
import com.lootfast.backend.merchant.performance.dto.AdvancedPerformanceResponse.Period;
import com.lootfast.backend.merchant.performance.repository.AdvancedPerformanceRepository;
import com.lootfast.backend.merchant.repository.MerchantProfileRepository;
import com.lootfast.backend.premium.service.PremiumEntitlementService;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AdvancedPerformanceService {
  private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
  private final TransactionTemplate snapshot;
  private final MerchantProfileRepository merchants;
  private final AdvancedPerformanceRepository reports;
  private final PremiumEntitlementService premium;
  private final AdvancedReportRateLimiter limiter;

  public AdvancedPerformanceService(
      MerchantProfileRepository merchants,
      AdvancedPerformanceRepository reports,
      PremiumEntitlementService premium,
      AdvancedReportRateLimiter limiter,
      PlatformTransactionManager transactions) {
    this.snapshot = new TransactionTemplate(transactions);
    this.snapshot.setReadOnly(true);
    this.snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    this.snapshot.setTimeout(10);
    this.merchants = merchants;
    this.reports = reports;
    this.premium = premium;
    this.limiter = limiter;
  }

  private Period period(LocalDate from, LocalDate to) {
    if (from == null
        || to == null
        || from.isAfter(to)
        || from.getYear() < 1
        || to.getYear() > 9999
        || ChronoUnit.DAYS.between(from, to) >= 366)
      throw new ApiException(
          ErrorCode.INVALID_REPORT_RANGE,
          "Each period must contain 1 to 366 days, years 1 to 9999");
    return new Period(from, to, ChronoUnit.DAYS.between(from, to) + 1);
  }

  public AdvancedPerformanceResponse get(
      String subject,
      LocalDate from,
      LocalDate to,
      LocalDate compareFrom,
      LocalDate compareTo,
      int page,
      int size) {
    Period current = period(from, to);
    if ((compareFrom == null) != (compareTo == null))
      throw new ApiException(ErrorCode.INVALID_REPORT_RANGE, "Provide both comparison dates");
    Period previous =
        compareFrom == null
            ? period(from.minusDays(current.days()), from.minusDays(1))
            : period(compareFrom, compareTo);
    if (!previous.to().isBefore(current.from()))
      throw new ApiException(
          ErrorCode.INVALID_REPORT_RANGE,
          "Comparison period must precede the current period without overlap");
    if (page < 0 || size < 1 || size > 50 || (long) page * size > Integer.MAX_VALUE)
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "Page size must be 1 to 50");
    var prechecked = premium.reportUser(subject);
    // The HTTP flow releases the precheck connection before waiting for Redis.
    limiter.requireAllowed(prechecked.getId());
    var result =
        snapshot.execute(
            status -> {
              var user =
                  premium.reportUser(
                      subject); // Recheck expiry/revocation in the reporting snapshot.
              var merchant =
                  merchants
                      .findByUserId(user.getId())
                      .orElseThrow(() -> new ApiException(ErrorCode.MERCHANT_PROFILE_NOT_FOUND));
              Instant start = from.atStartOfDay(ZONE).toInstant(),
                  end = to.plusDays(1).atStartOfDay(ZONE).toInstant();
              var a = reports.summary(merchant.getId(), start, end);
              var b =
                  reports.summary(
                      merchant.getId(),
                      previous.from().atStartOfDay(ZONE).toInstant(),
                      previous.to().plusDays(1).atStartOfDay(ZONE).toInstant());
              var comparison =
                  new Comparison(
                      ReportMath.change(a.completedRevenue(), b.completedRevenue()),
                      ReportMath.change(
                          BigDecimal.valueOf(a.placedOrders()),
                          BigDecimal.valueOf(b.placedOrders())),
                      ReportMath.change(
                          BigDecimal.valueOf(a.discountedPortions()),
                          BigDecimal.valueOf(b.discountedPortions())),
                      ReportMath.points(a.completionRatePercent(), b.completionRatePercent()),
                      ReportMath.points(a.cancellationRatePercent(), b.cancellationRatePercent()),
                      ReportMath.points(a.noShowRatePercent(), b.noShowRatePercent()));
              return new AdvancedPerformanceResponse(
                  ZONE.getId(),
                  current,
                  previous,
                  current.days() == previous.days(),
                  a,
                  b,
                  comparison,
                  reports.deals(merchant.getId(), start, end, page, size));
            });
    if (result == null) throw new IllegalStateException("Report snapshot returned no result");
    return result;
  }
}
