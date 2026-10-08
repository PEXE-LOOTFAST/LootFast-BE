package com.lootfast.backend.premium.catalog.controller;

import com.lootfast.backend.common.response.*;
import com.lootfast.backend.premium.catalog.dto.*;
import com.lootfast.backend.premium.catalog.dto.PlanRequests.*;
import com.lootfast.backend.premium.catalog.service.PlanCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/premium")
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin Premium plans", description = "Cấu hình phiên bản gói; chưa tạo thanh toán")
public class AdminPlanController {
  private final PlanCatalogService service;

  public AdminPlanController(PlanCatalogService service) {
    this.service = service;
  }

  @PostMapping("/plans")
  @Operation(summary = "Tạo gói và bản nháp đầu tiên")
  public ApiResponse<PlanResponse> create(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Create r) {
    return ApiResponse.success(service.create(jwt.getSubject(), r));
  }

  @GetMapping("/plans")
  @Operation(summary = "Xem phiên bản gói, gồm draft và archive; size 1–50")
  public ApiResponse<PagedResponse<PlanResponse>> list(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) PlanAudience audience,
      @RequestParam(required = false) String code,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.success(service.list(jwt.getSubject(), true, audience, code, page, size));
  }

  @PostMapping("/plans/{code}/revisions")
  @Operation(summary = "Tạo bản nháp mới; giữ nguyên code và audience")
  public ApiResponse<PlanResponse> draft(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable String code,
      @Valid @RequestBody Configuration r) {
    return ApiResponse.success(service.draft(jwt.getSubject(), code, r));
  }

  @GetMapping("/plan-revisions/{id}")
  @Operation(summary = "Xem cấu hình phiên bản")
  public ApiResponse<PlanResponse> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return ApiResponse.success(service.get(jwt.getSubject(), id, true));
  }

  @PutMapping("/plan-revisions/{id}")
  @Operation(summary = "Sửa draft với expectedRevision; không sửa bản đã công bố")
  public ApiResponse<PlanResponse> edit(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody Edit r) {
    return ApiResponse.success(service.edit(jwt.getSubject(), id, r));
  }

  @PostMapping("/plan-revisions/{id}/publish")
  @Operation(
      summary = "Công bố bản nháp, thay bản đang bán",
      description =
          "Điều khoản phải đã phát hành. Bản đang bán cũ được archive trong cùng transaction; nội"
              + " dung và lịch sử giữ nguyên. Công bố catalog chưa mở thanh toán VNPay.")
  public ApiResponse<PlanResponse> publish(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody Change r) {
    return ApiResponse.success(service.change(jwt.getSubject(), id, r, true));
  }

  @PostMapping("/plan-revisions/{id}/archive")
  @Operation(summary = "Ngừng bán phiên bản hoặc bỏ bản nháp; không xóa lịch sử")
  public ApiResponse<PlanResponse> archive(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody Change r) {
    return ApiResponse.success(service.change(jwt.getSubject(), id, r, false));
  }

  @GetMapping("/plans/{code}/audit")
  @Operation(summary = "Audit cấu hình gói và snapshot từng thao tác; phân trang")
  public ApiResponse<PagedResponse<PlanAuditResponse>> history(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable String code,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.success(service.history(jwt.getSubject(), code, page, size));
  }
}
