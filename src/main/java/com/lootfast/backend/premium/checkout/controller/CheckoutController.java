package com.lootfast.backend.premium.checkout.controller;

import com.lootfast.backend.common.response.*;
import com.lootfast.backend.premium.checkout.dto.CheckoutDtos.*;
import com.lootfast.backend.premium.checkout.service.CheckoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/premium/orders")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Premium checkout")
public class CheckoutController {
  private final CheckoutService service;

  public CheckoutController(CheckoutService service) {
    this.service = service;
  }

  @PostMapping
  @Operation(
      summary = "Tạo đơn Premium; Idempotency-Key UUID",
      description =
          "Giá và quyền lấy từ gói đang publish. Đồng ý đúng termsDocumentId. Không cấp quyền hoặc"
              + " nhận payment-success từ frontend.")
  public ApiResponse<Order> create(
      @AuthenticationPrincipal Jwt jwt,
      @RequestHeader("Idempotency-Key") UUID key,
      @Valid @RequestBody Create r) {
    return ApiResponse.success(service.create(jwt.getSubject(), key, r));
  }

  @PostMapping("/{id}/payment-attempts")
  @Operation(
      summary = "Khởi tạo hoặc trả lại URL VNPay sandbox",
      description =
          "Chỉ một attempt mỗi đơn. URL hết hạn không chứng minh giao dịch thất bại. VNPay mặc định"
              + " tắt khi chưa cấu hình khóa; kết quả được xác minh qua IPN hoặc QueryDr.")
  public ApiResponse<Payment> pay(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, HttpServletRequest request) {
    return ApiResponse.success(service.pay(jwt.getSubject(), id, request.getRemoteAddr()));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Xem đơn của mình")
  public ApiResponse<Order> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return ApiResponse.success(service.get(jwt.getSubject(), id));
  }

  @GetMapping
  @Operation(summary = "Danh sách đơn Premium của mình; size 1–50")
  public ApiResponse<PagedResponse<Order>> list(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.success(service.list(jwt.getSubject(), page, size));
  }
}
