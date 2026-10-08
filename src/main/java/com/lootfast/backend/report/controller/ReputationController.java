package com.lootfast.backend.report.controller;

import com.lootfast.backend.common.response.ApiResponse;
import com.lootfast.backend.report.dto.StoreReputationResponse;
import com.lootfast.backend.report.service.ReputationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stores")
@Tag(name = "Stores")
public class ReputationController {
  private final ReputationService service;

  public ReputationController(ReputationService service) {
    this.service = service;
  }

  @GetMapping("/{storeId}/reputation")
  @Operation(summary = "Điểm uy tín cửa hàng tính từ tỷ lệ hoàn tất và báo cáo vi phạm")
  public ApiResponse<StoreReputationResponse> reputation(@PathVariable UUID storeId) {
    return ApiResponse.success(service.getByStorePublicId(storeId));
  }
}
