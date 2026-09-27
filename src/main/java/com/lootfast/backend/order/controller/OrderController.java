package com.lootfast.backend.order.controller;

import com.lootfast.backend.common.response.ApiResponse;
import com.lootfast.backend.order.dto.*;
import com.lootfast.backend.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders")
@PreAuthorize("hasRole('BUYER')")
public class OrderController {
  private final OrderService service;

  public OrderController(OrderService service) {
    this.service = service;
  }

  @PostMapping
  @Operation(
      summary = "Đặt giữ deal và trừ kho đúng một lần; gửi lại cùng Idempotency-Key trong 24 giờ")
  public ResponseEntity<ApiResponse<OrderResponse>> reserve(
      @AuthenticationPrincipal Jwt jwt,
      @RequestHeader("Idempotency-Key") UUID key,
      @Valid @RequestBody CreateOrderRequest request) {
    var result = service.reserve(jwt.getSubject(), key, request);
    return ResponseEntity.created(URI.create("/api/v1/orders/" + result.orderId()))
        .body(ApiResponse.success(result));
  }

  @GetMapping("/{orderId}")
  @Operation(summary = "Đọc trạng thái hiện tại của đơn thuộc người mua")
  public ApiResponse<OrderResponse> get(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId) {
    return ApiResponse.success(service.get(jwt.getSubject(), orderId));
  }
}
