package com.lootfast.backend.report.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Điểm uy tín cửa hàng, tính on-the-fly. {@code completionRate} và {@code reputationScore} là null
 * khi cửa hàng chưa có đơn nào đến mốc nhận (chưa đủ dữ liệu). Thành phần "đánh giá" (046) chưa tính.
 */
public record StoreReputationResponse(
    UUID storeId,
    long completedOrders,
    long totalFinalizedOrders,
    BigDecimal completionRate,
    long outOfStockReports,
    Integer reputationScore,
    boolean underReview) {}
