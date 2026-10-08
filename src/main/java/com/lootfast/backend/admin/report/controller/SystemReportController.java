package com.lootfast.backend.admin.report.controller;

import com.lootfast.backend.admin.report.dto.SystemReportResponse;
import com.lootfast.backend.admin.report.service.SystemReportService;
import com.lootfast.backend.common.response.ApiResponse;
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
@RequestMapping("/api/v1/admin/reports")
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin reports", description = "Báo cáo toàn hệ thống")
public class SystemReportController {
  private final SystemReportService service;

  public SystemReportController(SystemReportService service) {
    this.service = service;
  }

  @GetMapping("/overview")
  @Operation(
      summary = "Xem tổng quan hoạt động LootFast",
      description =
          "from/to yyyy-MM-dd, bao gồm hai ngày, tối đa 366 ngày, múi giờ Việt Nam. Đơn đặt dùng"
              + " ngày tạo và trạng thái hiện tại; đơn hoàn thành dùng finalizedAt."
              + " completedTransactionValue chỉ tính COMPLETED + CONFIRMED, là giá trị giao dịch"
              + " cửa hàng, không phải doanh thu nền tảng. Tổng tài khoản/cửa hàng/trạng thái deal"
              + " là hiện tại; createdDeals bao gồm bản nháp, không phải số lần xuất bản. Tài khoản"
              + " nhiều role được tính trong từng role. Top 10 xếp theo giá trị giao dịch rồi số"
              + " đơn hoàn thành. Giới hạn 10 báo cáo/admin/phút; ngày không có hoạt động trả 0.")
  public ApiResponse<SystemReportResponse> get(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return ApiResponse.success(service.get(jwt.getSubject(), from, to));
  }
}
