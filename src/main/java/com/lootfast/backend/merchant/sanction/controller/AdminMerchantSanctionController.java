package com.lootfast.backend.merchant.sanction.controller;

import com.lootfast.backend.common.response.ApiResponse;
import com.lootfast.backend.common.response.PagedResponse;
import com.lootfast.backend.merchant.sanction.dto.MerchantSanctionResponse;
import com.lootfast.backend.merchant.sanction.dto.SuspendMerchantRequest;
import com.lootfast.backend.merchant.sanction.dto.WarnMerchantRequest;
import com.lootfast.backend.merchant.sanction.service.MerchantSanctionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin - Merchant sanctions", description = "Cảnh cáo và đình chỉ cửa hàng")
@Validated
@RestController
@RequestMapping("/api/v1/admin/merchants")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMerchantSanctionController {
  private final MerchantSanctionService service;

  public AdminMerchantSanctionController(MerchantSanctionService service) {
    this.service = service;
  }

  @PostMapping("/{storeId}/sanctions/warnings")
  @Operation(summary = "Cảnh cáo cửa hàng", security = @SecurityRequirement(name = "bearerAuth"))
  public ApiResponse<MerchantSanctionResponse> warn(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID storeId,
      @Valid @RequestBody WarnMerchantRequest request) {
    return ApiResponse.success(service.warn(jwt.getSubject(), storeId, request.reason()));
  }

  @PostMapping("/{storeId}/sanctions/suspensions")
  @Operation(
      summary = "Đình chỉ cửa hàng có thời hạn",
      security = @SecurityRequirement(name = "bearerAuth"))
  public ApiResponse<MerchantSanctionResponse> suspend(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID storeId,
      @Valid @RequestBody SuspendMerchantRequest request) {
    return ApiResponse.success(
        service.suspend(jwt.getSubject(), storeId, request.reason(), request.durationDays()));
  }

  @PostMapping("/sanctions/{sanctionId}/lift")
  @Operation(
      summary = "Kết thúc sớm một cảnh cáo/đình chỉ",
      security = @SecurityRequirement(name = "bearerAuth"))
  public ApiResponse<MerchantSanctionResponse> lift(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID sanctionId) {
    return ApiResponse.success(service.lift(jwt.getSubject(), sanctionId));
  }

  @GetMapping("/{storeId}/sanctions")
  @Operation(
      summary = "Lịch sử xử phạt của một cửa hàng",
      security = @SecurityRequirement(name = "bearerAuth"))
  public ApiResponse<PagedResponse<MerchantSanctionResponse>> list(
      @PathVariable UUID storeId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
    return ApiResponse.success(service.list(storeId, page, size));
  }
}
