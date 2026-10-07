package com.lootfast.backend.premium.controller;

import com.lootfast.backend.common.response.*;
import com.lootfast.backend.premium.dto.*;
import com.lootfast.backend.premium.service.PremiumEntitlementService;
import com.lootfast.backend.user.admin.dto.AccountActionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/premium/entitlements")
@PreAuthorize("hasRole('ADMIN')")
@Validated
@Tag(name = "Premium trial entitlements")
@SecurityRequirement(name = "bearerAuth")
public class PremiumEntitlementController {
  private final PremiumEntitlementService service;

  public PremiumEntitlementController(PremiumEntitlementService service) {
    this.service = service;
  }

  @PostMapping
  @Operation(
      summary = "Admin cấp quyền Premium có thời hạn; không ghi nhận thanh toán",
      description =
          "id là UUID của request, gửi lại cùng payload để retry an toàn. Chỉ dành cho dùng thử/cấp"
              + " quyền thủ công; không thay thế IPN.")
  public ApiResponse<PremiumEntitlement> grant(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody GrantPremiumRequest r) {
    return ApiResponse.success(service.grant(jwt.getSubject(), r));
  }

  @PostMapping("/{id}/revoke")
  @Operation(summary = "Thu hồi một lượt cấp quyền; giữ lịch sử và không sửa lần thu hồi trước")
  public ApiResponse<PremiumEntitlement> revoke(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody AccountActionRequest r) {
    return ApiResponse.success(service.revoke(jwt.getSubject(), id, r.reason()));
  }

  @GetMapping
  @Operation(summary = "Admin xem lịch sử quyền của một tài khoản")
  public ApiResponse<PagedResponse<PremiumEntitlement>> list(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam UUID userId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
    return ApiResponse.success(service.list(jwt.getSubject(), userId, page, size));
  }
}
