package com.lootfast.backend.support.controller;

import com.lootfast.backend.common.response.ApiResponse;
import com.lootfast.backend.common.response.PagedResponse;
import com.lootfast.backend.support.dto.TicketDecisionRequest;
import com.lootfast.backend.support.dto.TicketDetailResponse;
import com.lootfast.backend.support.dto.TicketResponse;
import com.lootfast.backend.support.entity.TicketStatus;
import com.lootfast.backend.support.service.AdminTicketService;
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

@Tag(name = "Admin - Tickets", description = "Phân loại và xử lý ticket/tranh chấp")
@Validated
@RestController
@RequestMapping("/api/v1/admin/tickets")
@PreAuthorize("hasRole('ADMIN')")
public class AdminTicketController {
  private final AdminTicketService service;

  public AdminTicketController(AdminTicketService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(summary = "Danh sách ticket, lọc theo trạng thái", security = @SecurityRequirement(name = "bearerAuth"))
  public ApiResponse<PagedResponse<TicketResponse>> list(
      @RequestParam(required = false) TicketStatus status,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
    return ApiResponse.success(service.list(status, page, size));
  }

  @GetMapping("/{ticketId}")
  @Operation(summary = "Chi tiết ticket kèm lịch sử xử lý", security = @SecurityRequirement(name = "bearerAuth"))
  public ApiResponse<TicketDetailResponse> detail(@PathVariable UUID ticketId) {
    return ApiResponse.success(service.getDetail(ticketId));
  }

  @PostMapping("/{ticketId}/review")
  @Operation(summary = "Nhận xử lý ticket", security = @SecurityRequirement(name = "bearerAuth"))
  public ApiResponse<TicketResponse> review(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID ticketId) {
    return ApiResponse.success(service.review(jwt.getSubject(), ticketId));
  }

  @PostMapping("/{ticketId}/resolve")
  @Operation(summary = "Giải quyết ticket kèm lý do", security = @SecurityRequirement(name = "bearerAuth"))
  public ApiResponse<TicketResponse> resolve(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID ticketId,
      @Valid @RequestBody TicketDecisionRequest request) {
    return ApiResponse.success(service.resolve(jwt.getSubject(), ticketId, request.note()));
  }

  @PostMapping("/{ticketId}/reject")
  @Operation(summary = "Từ chối ticket kèm lý do", security = @SecurityRequirement(name = "bearerAuth"))
  public ApiResponse<TicketResponse> reject(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID ticketId,
      @Valid @RequestBody TicketDecisionRequest request) {
    return ApiResponse.success(service.reject(jwt.getSubject(), ticketId, request.note()));
  }

  @PostMapping("/{ticketId}/close")
  @Operation(summary = "Đóng ticket", security = @SecurityRequirement(name = "bearerAuth"))
  public ApiResponse<TicketResponse> close(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID ticketId) {
    return ApiResponse.success(service.close(jwt.getSubject(), ticketId));
  }
}
