package com.lootfast.backend.admin.report.service;

import com.lootfast.backend.admin.report.dto.SystemReportResponse;
import com.lootfast.backend.admin.report.dto.SystemReportResponse.Daily;
import com.lootfast.backend.admin.report.repository.SystemReportRepository;
import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.user.entity.User;
import com.lootfast.backend.user.entity.UserRole;
import com.lootfast.backend.user.entity.UserStatus;
import com.lootfast.backend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SystemReportService {
  private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
  private final UserRepository users;
  private final SystemReportRepository reports;
  private final SystemReportRateLimiter limiter;
  private final Clock clock;
  private final TransactionTemplate precheck, snapshot;

  public SystemReportService(
      UserRepository users,
      SystemReportRepository reports,
      SystemReportRateLimiter limiter,
      Clock clock,
      PlatformTransactionManager transactions) {
    this.users = users;
    this.reports = reports;
    this.limiter = limiter;
    this.clock = clock;
    precheck = new TransactionTemplate(transactions);
    precheck.setReadOnly(true);
    precheck.setTimeout(5);
    snapshot = new TransactionTemplate(transactions);
    snapshot.setReadOnly(true);
    snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    snapshot.setTimeout(10);
  }

  private User admin(String subject) {
    long id;
    try {
      id = Long.parseLong(subject);
    } catch (RuntimeException e) {
      throw new ApiException(ErrorCode.ACCESS_DENIED);
    }
    var user =
        users.findByIdWithRoles(id).orElseThrow(() -> new ApiException(ErrorCode.ACCESS_DENIED));
    if (!user.hasRole(UserRole.ADMIN) || user.isLocked() || user.getStatus() != UserStatus.VERIFIED)
      throw new ApiException(ErrorCode.ACCESS_DENIED);
    return user;
  }

  public SystemReportResponse get(String subject, LocalDate from, LocalDate to) {
    if (from == null
        || to == null
        || from.isAfter(to)
        || from.getYear() < 1
        || to.getYear() > 9998
        || ChronoUnit.DAYS.between(from, to) >= 366)
      throw new ApiException(
          ErrorCode.INVALID_REPORT_RANGE, "Provide 1 to 366 inclusive days, years 1 to 9998");
    var actor = precheck.execute(status -> admin(subject));
    if (actor == null) throw new IllegalStateException("Missing report actor");
    // No SQL connection is held while the HTTP flow waits for Redis.
    limiter.requireAllowed(actor.getId());
    return snapshot.execute(
        status -> {
          admin(subject);
          Instant now = clock.instant(),
              start = from.atStartOfDay(ZONE).toInstant(),
              end = to.plusDays(1).atStartOfDay(ZONE).toInstant();
          var accounts = reports.accounts(start, end);
          var stores = reports.stores();
          var deals = reports.deals(start, end, now);
          var orders = reports.placed(start, end);
          var rows = new HashMap<LocalDate, Daily>();
          reports.daily(start, end).forEach(row -> rows.put(row.date(), row));
          var daily = new ArrayList<Daily>();
          long completed = 0, portions = 0;
          var value = BigDecimal.ZERO;
          var savings = BigDecimal.ZERO;
          for (var date = from; !date.isAfter(to); date = date.plusDays(1)) {
            var row =
                rows.getOrDefault(date, new Daily(date, 0, 0, BigDecimal.ZERO, 0, BigDecimal.ZERO));
            daily.add(row);
            completed += row.completedOrders();
            portions += row.discountedPortions();
            value = value.add(row.completedTransactionValue());
            savings = savings.add(row.customerSavings());
          }
          return new SystemReportResponse(
              from,
              to,
              ZONE.getId(),
              now,
              accounts,
              stores,
              deals,
              orders.values().stream().mapToLong(Long::longValue).sum(),
              orders,
              completed,
              value,
              portions,
              savings,
              java.util.List.copyOf(daily),
              reports.topStores(start, end));
        });
  }
}
