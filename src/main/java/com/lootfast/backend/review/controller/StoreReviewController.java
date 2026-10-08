package com.lootfast.backend.review.controller;

import com.lootfast.backend.common.response.*;
import com.lootfast.backend.review.dto.ReviewRequests.*;
import com.lootfast.backend.review.dto.ReviewResponses.*;
import com.lootfast.backend.review.service.StoreReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Store reviews")
@Validated
public class StoreReviewController {
  private final StoreReviewService service;
  public StoreReviewController(StoreReviewService service) { this.service = service; }

  @PostMapping("/orders/{orderId}/review") @PreAuthorize("hasRole('BUYER')")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "Đánh giá đơn đã giao: 1–5 sao, mỗi đơn một lần")
  public ApiResponse<ManagedReview> create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId, @Valid @RequestBody Create request) {
    return ApiResponse.success(service.create(jwt.getSubject(), orderId, request));
  }
  @GetMapping("/orders/{orderId}/review") @PreAuthorize("hasRole('BUYER')")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "Xem đánh giá của chính mình cho một đơn")
  public ApiResponse<ManagedReview> mine(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId) {
    return ApiResponse.success(service.mine(jwt.getSubject(), orderId));
  }
  @GetMapping("/stores/{merchantId}/reviews")
  @Operation(summary = "Danh sách đánh giá công khai; loại bỏ đánh giá bị ẩn")
  public ApiResponse<PagedResponse<PublicReview>> list(@PathVariable UUID merchantId,
      @RequestParam(defaultValue="0") @Min(0) int page, @RequestParam(defaultValue="20") @Min(1) @Max(50) int size) {
    return ApiResponse.success(service.publicList(merchantId, page, size));
  }
  @GetMapping("/merchants/me/reviews") @PreAuthorize("hasRole('MERCHANT')")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "Merchant xem đánh giá cửa hàng của mình, gồm đánh giá bị ẩn")
  public ApiResponse<PagedResponse<ManagedReview>> merchantList(@AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue="0") @Min(0) int page, @RequestParam(defaultValue="20") @Min(1) @Max(50) int size) {
    return ApiResponse.success(service.merchantList(jwt.getSubject(), page, size));
  }
  @PutMapping("/merchants/me/reviews/{reviewId}/reply") @PreAuthorize("hasRole('MERCHANT')")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "Phản hồi một lần; gửi lại cùng nội dung không tạo phản hồi mới")
  public ApiResponse<ManagedReview> reply(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID reviewId, @Valid @RequestBody Reply request) {
    return ApiResponse.success(service.reply(jwt.getSubject(), reviewId, request));
  }
  @GetMapping("/admin/reviews") @PreAuthorize("hasRole('ADMIN')")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "Admin xem đánh giá để kiểm duyệt")
  public ApiResponse<PagedResponse<ManagedReview>> adminList(@AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue="0") @Min(0) int page, @RequestParam(defaultValue="20") @Min(1) @Max(50) int size) {
    return ApiResponse.success(service.adminList(jwt.getSubject(), page, size));
  }
  @PutMapping("/admin/reviews/{reviewId}/visibility") @PreAuthorize("hasRole('ADMIN')")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "Ẩn/hiện đánh giá với lý do; lưu lịch sử thay đổi")
  public ApiResponse<ManagedReview> visibility(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID reviewId, @Valid @RequestBody Visibility request) {
    return ApiResponse.success(service.visibility(jwt.getSubject(), reviewId, request));
  }
  @GetMapping("/admin/reviews/{reviewId}/moderation-history") @PreAuthorize("hasRole('ADMIN')")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(summary = "Lịch sử ẩn/hiện đánh giá, chỉ admin được đọc")
  public ApiResponse<PagedResponse<Moderation>> history(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID reviewId,
      @RequestParam(defaultValue="0") @Min(0) int page, @RequestParam(defaultValue="20") @Min(1) @Max(50) int size) {
    return ApiResponse.success(service.history(jwt.getSubject(), reviewId, page, size));
  }
}
