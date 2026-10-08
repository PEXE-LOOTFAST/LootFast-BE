package com.lootfast.backend.report.service;

import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.merchant.entity.MerchantProfile;
import com.lootfast.backend.merchant.repository.MerchantProfileRepository;
import com.lootfast.backend.order.entity.OrderStatus;
import com.lootfast.backend.order.repository.OrderRepository;
import com.lootfast.backend.report.dto.StoreReputationResponse;
import com.lootfast.backend.report.entity.ReportType;
import com.lootfast.backend.report.repository.StoreReportRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tính điểm uy tín cửa hàng trực tiếp từ dữ liệu vận hành (không lưu điểm). Công thức MVP: điểm cơ sở
 * = tỷ lệ hoàn tất × 100, trừ bớt theo số báo "hết hàng". Thành phần đánh giá (046) sẽ cộng sau.
 */
@Service
public class ReputationService {
  // Mẫu số tỷ lệ hoàn tất: chỉ các kết cục thuộc trách nhiệm cửa hàng. KHÔNG tính NO_SHOW vì đó là
  // lỗi của khách (đã bị phạt riêng ở task 038), không phải lỗi cửa hàng.
  private static final Set<OrderStatus> MERCHANT_ACCOUNTABLE =
      Set.of(OrderStatus.COMPLETED, OrderStatus.MERCHANT_CANCELLED);
  private static final int PENALTY_PER_OUT_OF_STOCK = 5;
  private static final int MAX_OUT_OF_STOCK_PENALTY = 30;

  private final MerchantProfileRepository merchants;
  private final OrderRepository orders;
  private final StoreReportRepository reports;

  public ReputationService(
      MerchantProfileRepository merchants,
      OrderRepository orders,
      StoreReportRepository reports) {
    this.merchants = merchants;
    this.orders = orders;
    this.reports = reports;
  }

  @Transactional(readOnly = true)
  public StoreReputationResponse getByStorePublicId(UUID storeId) {
    MerchantProfile merchant =
        merchants
            .findPublicByUserPublicId(storeId)
            .orElseThrow(
                () ->
                    new ApiException(ErrorCode.MERCHANT_PROFILE_NOT_FOUND, "Store is not available"));

    long completed = orders.countByMerchantIdAndStatus(merchant.getId(), OrderStatus.COMPLETED);
    long finalized = orders.countByMerchantIdAndStatusIn(merchant.getId(), MERCHANT_ACCOUNTABLE);
    long outOfStock =
        reports.countByMerchantIdAndReportType(merchant.getId(), ReportType.OUT_OF_STOCK);

    BigDecimal completionRate =
        finalized == 0
            ? null
            : BigDecimal.valueOf(completed).divide(BigDecimal.valueOf(finalized), 2, RoundingMode.HALF_UP);
    Integer score = finalized == 0 ? null : score(completionRate, outOfStock);

    return new StoreReputationResponse(
        storeId, completed, finalized, completionRate, outOfStock, score, merchant.isFlaggedForReview());
  }

  private Integer score(BigDecimal completionRate, long outOfStock) {
    int base = completionRate.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).intValue();
    int penalty = (int) Math.min(outOfStock * PENALTY_PER_OUT_OF_STOCK, MAX_OUT_OF_STOCK_PENALTY);
    return Math.max(0, Math.min(100, base - penalty));
  }
}
