package com.lootfast.backend.merchant.performance.controller;

import com.lootfast.backend.common.response.ApiResponse;
import com.lootfast.backend.merchant.performance.dto.AdvancedPerformanceResponse;
import com.lootfast.backend.merchant.performance.service.AdvancedPerformanceService;
import com.lootfast.backend.premium.service.PremiumEntitlementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@PreAuthorize("hasRole('MERCHANT')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Merchant advanced performance")
@Validated
public class AdvancedPerformanceController {
  private final AdvancedPerformanceService service;
  private final PremiumEntitlementService premium;

  public AdvancedPerformanceController(
      AdvancedPerformanceService service, PremiumEntitlementService premium) {
    this.service = service;
    this.premium = premium;
  }

  @GetMapping("/api/v1/merchants/me/reports/performance/advanced")
  @Operation(
      summary = "Premium: so sánh hai kỳ và hiệu quả từng deal",
      description =
          "Mỗi kỳ tối đa 366 ngày, giờ Việt Nam. Mặc định so với kỳ liền trước cùng số ngày. Tỷ lệ"
              + " tính theo trạng thái hiện tại của đơn tạo trong kỳ; hủy gồm khách và merchant."
              + " Doanh thu theo ngày hoàn tất của đơn COMPLETED + CONFIRMED. Phần trăm thay đổi"
              + " null khi kỳ trước bằng 0. Báo cáo cần quyền Premium từ database; tối đa 20"
              + " request/phút/merchant.")
  public ApiResponse<AdvancedPerformanceResponse> get(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate compareFrom,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate compareTo,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
    return ApiResponse.success(
        service.get(jwt.getSubject(), from, to, compareFrom, compareTo, page, size));
  }

  @GetMapping("/api/v1/merchants/me/premium/benefits")
  @Operation(summary = "Kiểm tra quyền báo cáo nâng cao của merchant hiện tại")
  public ApiResponse<Benefits> benefits(@AuthenticationPrincipal Jwt jwt) {
    return ApiResponse.success(new Benefits(premium.available(jwt.getSubject())));
  }

  public record Benefits(boolean advancedReports) {}
}
