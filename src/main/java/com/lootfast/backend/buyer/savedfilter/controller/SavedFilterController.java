package com.lootfast.backend.buyer.savedfilter.controller;

import com.lootfast.backend.buyer.savedfilter.dto.*;
import com.lootfast.backend.buyer.savedfilter.service.SavedFilterService;
import com.lootfast.backend.common.response.*;
import com.lootfast.backend.deal.dto.DealFeedItemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/buyers/me")
@PreAuthorize("hasRole('BUYER')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Buyer Premium saved filters")
@Validated
public class SavedFilterController {
  private final SavedFilterService service;

  public SavedFilterController(SavedFilterService service) {
    this.service = service;
  }

  @GetMapping("/premium/benefits")
  @Operation(summary = "Kiểm tra quyền dùng bộ lọc đã lưu của Buyer")
  public ApiResponse<SavedFilterService.Benefits> benefits(@AuthenticationPrincipal Jwt jwt) {
    return ApiResponse.success(service.benefits(jwt.getSubject()));
  }

  @PostMapping("/saved-filters")
  @Operation(
      summary = "Buyer Premium lưu bộ lọc deal, tối đa 10 bộ lọc",
      description =
          "Giờ nhận là giờ địa phương Việt Nam hằng ngày; thông báo mặc định tắt, bật bằng API"
              + " riêng.")
  public ApiResponse<SavedFilterResponse> create(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SavedFilterCriteria r) {
    return ApiResponse.success(service.create(jwt.getSubject(), r));
  }

  @GetMapping("/saved-filters")
  @Operation(summary = "Xem bộ lọc đã lưu, kể cả khi hết Premium")
  public ApiResponse<PagedResponse<SavedFilterResponse>> list(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
    return ApiResponse.success(service.list(jwt.getSubject(), page, size));
  }

  @GetMapping("/saved-filters/{id}")
  @Operation(summary = "Xem bộ lọc thuộc tài khoản hiện tại")
  public ApiResponse<SavedFilterResponse> get(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return ApiResponse.success(service.get(jwt.getSubject(), id));
  }

  @PutMapping("/saved-filters/{id}")
  @Operation(summary = "Buyer Premium sửa bộ lọc, kiểm tra expectedRevision để tránh ghi đè")
  public ApiResponse<SavedFilterResponse> update(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody UpdateSavedFilterRequest r) {
    return ApiResponse.success(service.update(jwt.getSubject(), id, r));
  }

  @PutMapping("/saved-filters/{id}/notifications")
  @Operation(
      summary = "Bật/tắt thông báo theo bộ lọc đã lưu",
      description =
          "Bật cần Buyer Premium. Tắt được phép khi hết Premium. Mặc định tắt; không gửi bù deal"
              + " cũ.")
  public ApiResponse<SavedFilterResponse> notifications(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody FilterNotificationRequest r) {
    return ApiResponse.success(service.notifications(jwt.getSubject(), id, r));
  }

  @DeleteMapping("/saved-filters/{id}")
  @Operation(summary = "Xóa bộ lọc của mình, được phép cả khi hết Premium")
  public ApiResponse<Void> delete(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @RequestParam @Min(0) long expectedRevision) {
    service.delete(jwt.getSubject(), id, expectedRevision);
    return ApiResponse.success(null);
  }

  @GetMapping("/saved-filters/{id}/deals")
  @Operation(
      summary = "Buyer Premium tìm deal theo bộ lọc đã lưu",
      description =
          "date mặc định hôm nay theo Asia/Ho_Chi_Minh. Chỉ deal đang mở bán, còn hàng; giờ nhận"
              + " giao với khoảng giờ đã lưu. Tối đa 30 lượt/phút/tài khoản.")
  public ApiResponse<PagedResponse<DealFeedItemResponse>> search(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @RequestParam(required = false) LocalDate date,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
    return ApiResponse.success(service.search(jwt.getSubject(), id, date, page, size));
  }
}
