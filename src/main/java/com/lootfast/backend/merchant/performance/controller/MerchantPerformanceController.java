package com.lootfast.backend.merchant.performance.controller;

import com.lootfast.backend.common.response.ApiResponse;
import com.lootfast.backend.merchant.performance.dto.MerchantPerformanceResponse;
import com.lootfast.backend.merchant.performance.service.MerchantPerformanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/merchants/me/reports")
@PreAuthorize("hasRole('MERCHANT')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Merchant performance", description = "Báo cáo hiệu quả cửa hàng")
public class MerchantPerformanceController {
  private final MerchantPerformanceService service;
  public MerchantPerformanceController(MerchantPerformanceService service) { this.service = service; }

  @GetMapping("/performance")
  @Operation(summary = "Xem doanh thu và phần ăn giảm giá của cửa hàng",
      description = "from/to định dạng yyyy-MM-dd, bao gồm hai ngày, tối đa 366 ngày, múi giờ Việt Nam. "
          + "Số đơn đặt dùng ngày tạo và trạng thái hiện tại. Doanh thu, phần ăn giảm giá và tiền tiết kiệm "
          + "dùng ngày hoàn tất; doanh thu chỉ tính đơn COMPLETED có thanh toán CONFIRMED. "
          + "Top 10 deal tính cùng điều kiện doanh thu và hiển thị tên deal hiện tại. Ngày trống trả 0.")
  public ApiResponse<MerchantPerformanceResponse> get(@AuthenticationPrincipal Jwt jwt,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return ApiResponse.success(service.get(jwt.getSubject(), from, to));
  }
}
