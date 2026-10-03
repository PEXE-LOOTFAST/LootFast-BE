package com.lootfast.backend.report.controller;

import com.lootfast.backend.common.response.ApiResponse;
import com.lootfast.backend.common.response.PagedResponse;
import com.lootfast.backend.report.dto.StoreReportResponse;
import com.lootfast.backend.report.entity.ReportType;
import com.lootfast.backend.report.service.StoreReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Store reports")
@Validated
@PreAuthorize("hasRole('BUYER')")
public class StoreReportController {
  private final StoreReportService service;

  public StoreReportController(StoreReportService service) {
    this.service = service;
  }

  @PostMapping(value = "/orders/{orderId}/reports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(summary = "Khách báo cáo vấn đề về một đơn; ảnh minh chứng tuỳ chọn")
  public ApiResponse<StoreReportResponse> create(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID orderId,
      @RequestParam ReportType type,
      @RequestParam @NotBlank @Size(max = 1000) String description,
      @RequestParam(required = false) MultipartFile evidence) {
    return ApiResponse.success(
        service.create(jwt.getSubject(), orderId, type, description, evidence));
  }

  @GetMapping("/reports")
  @Operation(summary = "Danh sách báo cáo của chính khách, phân trang")
  public ApiResponse<PagedResponse<StoreReportResponse>> listMine(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
    return ApiResponse.success(service.listMine(jwt.getSubject(), page, size));
  }
}
