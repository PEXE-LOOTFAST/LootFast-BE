package com.lootfast.backend.merchant.performance.service;

import com.lootfast.backend.merchant.performance.dto.AdvancedPerformanceResponse.Change;
import java.math.BigDecimal;
import java.math.RoundingMode;

public final class ReportMath {
  private ReportMath() {}

  public static BigDecimal rate(long count, long total) {
    return total == 0
        ? null
        : BigDecimal.valueOf(count)
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
  }

  public static Change change(BigDecimal current, BigDecimal previous) {
    var delta = current.subtract(previous);
    return new Change(
        delta,
        previous.signum() == 0
            ? null
            : delta.multiply(BigDecimal.valueOf(100)).divide(previous, 2, RoundingMode.HALF_UP),
        previous.signum() == 0);
  }

  public static BigDecimal points(BigDecimal current, BigDecimal previous) {
    return current == null || previous == null ? null : current.subtract(previous);
  }
}
