package com.lootfast.backend.merchant.performance.dto;

import com.lootfast.backend.common.response.PagedResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record AdvancedPerformanceResponse(
    String timezone,
    Period currentPeriod,
    Period comparisonPeriod,
    boolean samePeriodLength,
    Summary current,
    Summary previous,
    Comparison comparison,
    PagedResponse<DealPerformance> deals) {
  public record Period(LocalDate from, LocalDate to, long days) {}

  public record Summary(
      long placedOrders,
      long completedOrders,
      long customerCancelledOrders,
      long merchantCancelledOrders,
      long noShowOrders,
      long pendingOrders,
      BigDecimal completedRevenue,
      long discountedPortions,
      BigDecimal completionRatePercent,
      BigDecimal cancellationRatePercent,
      BigDecimal noShowRatePercent) {}

  public record Change(
      BigDecimal absoluteChange,
      @Schema(description = "null khi kỳ trước bằng 0; không suy diễn thành tăng 100%")
          BigDecimal percentChange,
      boolean zeroBaseline) {}

  public record Comparison(
      Change revenue,
      Change placedOrders,
      Change discountedPortions,
      BigDecimal completionRateChangePoints,
      BigDecimal cancellationRateChangePoints,
      BigDecimal noShowRateChangePoints) {}

  public record DealPerformance(
      UUID dealId,
      String name,
      long placedOrders,
      long completedOrders,
      long customerCancelledOrders,
      long merchantCancelledOrders,
      long noShowOrders,
      long pendingOrders,
      BigDecimal completedRevenue,
      BigDecimal completionRatePercent,
      BigDecimal cancellationRatePercent,
      BigDecimal noShowRatePercent) {}
}
