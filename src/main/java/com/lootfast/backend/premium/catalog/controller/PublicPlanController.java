package com.lootfast.backend.premium.catalog.controller;

import com.lootfast.backend.common.response.*;
import com.lootfast.backend.premium.catalog.dto.*;
import com.lootfast.backend.premium.catalog.service.PlanCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/premium/plans")
@Tag(name = "Premium catalog")
public class PublicPlanController {
  private final PlanCatalogService service;

  public PublicPlanController(PlanCatalogService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(
      summary = "Gói đang công bố cho buyer/merchant; size 1–50",
      description =
          "Không cần đăng nhập. Chỉ PUBLISHED, không có draft/archive hoặc audit. durationDays là"
              + " số ngày, giá VND nguyên. Catalog chưa có checkout ở task 076.")
  public ApiResponse<PagedResponse<PlanResponse>> list(
      @RequestParam(required = false) PlanAudience audience,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.success(service.list(null, false, audience, null, page, size));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Xem một phiên bản đang công bố; draft/archive trả 404")
  public ApiResponse<PlanResponse> get(@PathVariable UUID id) {
    return ApiResponse.success(service.get(null, id, false));
  }
}
