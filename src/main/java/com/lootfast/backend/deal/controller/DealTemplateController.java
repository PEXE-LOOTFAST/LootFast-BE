package com.lootfast.backend.deal.controller;

import com.lootfast.backend.common.response.ApiResponse;
import com.lootfast.backend.common.response.PagedResponse;
import com.lootfast.backend.deal.dto.DealTemplateResponse;
import com.lootfast.backend.deal.dto.MerchantDealResponse;
import com.lootfast.backend.deal.dto.ReuseDealTemplateRequest;
import com.lootfast.backend.deal.service.DealTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Deal templates", description = "Lưu và tái sử dụng thông tin deal")
@Validated
@RestController
@RequestMapping("/api/v1/merchants/me/deal-templates")
@PreAuthorize("hasRole('MERCHANT')")
@SecurityRequirement(name = "bearerAuth")
public class DealTemplateController {
  private final DealTemplateService service;

  public DealTemplateController(DealTemplateService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(summary = "Danh sách mẫu deal của cửa hàng")
  public ApiResponse<PagedResponse<DealTemplateResponse>> list(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
    return ApiResponse.success(service.list(jwt.getSubject(), page, size));
  }

  @PostMapping("/{templateId}/deals")
  @Operation(summary = "Tạo bản nháp deal mới từ mẫu")
  public ApiResponse<MerchantDealResponse> reuse(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID templateId,
      @Valid @RequestBody ReuseDealTemplateRequest request) {
    return ApiResponse.success(service.reuse(jwt.getSubject(), templateId, request));
  }

  @DeleteMapping("/{templateId}")
  @Operation(summary = "Xóa mẫu deal")
  public ResponseEntity<Void> delete(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID templateId) {
    service.delete(jwt.getSubject(), templateId);
    return ResponseEntity.noContent().build();
  }
}
