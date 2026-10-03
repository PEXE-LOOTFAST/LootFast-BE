package com.lootfast.backend.support.controller;

import com.lootfast.backend.common.response.ApiResponse;
import com.lootfast.backend.common.response.PagedResponse;
import com.lootfast.backend.support.dto.TicketResponse;
import com.lootfast.backend.support.entity.TicketCategory;
import com.lootfast.backend.support.service.SupportTicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/tickets")
@Tag(name = "Support tickets")
@Validated
public class SupportTicketController {
  private final SupportTicketService service;

  public SupportTicketController(SupportTicketService service) {
    this.service = service;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(summary = "Tạo ticket hỗ trợ/khiếu nại; ảnh minh chứng và đơn/cửa hàng liên quan tuỳ chọn")
  public ApiResponse<TicketResponse> create(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam TicketCategory category,
      @RequestParam @NotBlank @Size(max = 160) String title,
      @RequestParam @NotBlank @Size(max = 2000) String description,
      @RequestParam(required = false) UUID orderId,
      @RequestParam(required = false) UUID storeId,
      @RequestParam(required = false) MultipartFile evidence) {
    return ApiResponse.success(
        service.create(jwt.getSubject(), category, title, description, orderId, storeId, evidence));
  }

  @GetMapping
  @Operation(summary = "Danh sách ticket của chính người dùng, phân trang")
  public ApiResponse<PagedResponse<TicketResponse>> listMine(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
    return ApiResponse.success(service.listMine(jwt.getSubject(), page, size));
  }

  @GetMapping("/{ticketId}")
  @Operation(summary = "Xem ticket của chính người dùng")
  public ApiResponse<TicketResponse> get(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID ticketId) {
    return ApiResponse.success(service.get(jwt.getSubject(), ticketId));
  }
}
