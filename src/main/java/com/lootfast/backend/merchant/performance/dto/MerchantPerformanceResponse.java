package com.lootfast.backend.merchant.performance.dto;

import com.lootfast.backend.order.entity.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record MerchantPerformanceResponse(
    LocalDate from, LocalDate to, String timezone,
    @Schema(description = "Số đơn tạo trong khoảng ngày chọn, phân loại theo trạng thái hiện tại")
    long placedOrders,
    Map<OrderStatus, Long> ordersByStatus,
    @Schema(description = "Doanh thu VND của đơn đã hoàn tất và xác nhận thanh toán, theo ngày hoàn tất")
    BigDecimal completedRevenue,
    @Schema(description = "Phần ăn giảm giá: tổng số lượng của đơn đã hoàn tất, theo ngày hoàn tất")
    long discountedPortions,
    @Schema(description = "Tiền khách tiết kiệm theo giá được lưu khi đặt đơn đã hoàn tất")
    BigDecimal customerSavings,
    List<DailyPerformance> daily,
    List<TopDeal> topDeals) {
  public record DailyPerformance(LocalDate date, BigDecimal completedRevenue,
      long discountedPortions, BigDecimal customerSavings) {}
  public record TopDeal(UUID dealId, String name, long completedOrders,
      long discountedPortions, BigDecimal completedRevenue) {}
}
