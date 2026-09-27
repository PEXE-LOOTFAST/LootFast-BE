package com.lootfast.backend.payment.controller;

import com.lootfast.backend.common.response.ApiResponse;
import com.lootfast.backend.order.dto.OrderResponse;
import com.lootfast.backend.order.entity.PaymentMethod;
import com.lootfast.backend.payment.dto.*;
import com.lootfast.backend.payment.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders/{orderId}")
@Tag(name = "Order payments")
@PreAuthorize("hasRole('BUYER')")
public class PaymentController {
  private final PaymentTransactionService transactions;

  public PaymentController(PaymentTransactionService transactions) {
    this.transactions = transactions;
  }

  @GetMapping("/payment-instructions")
  @Operation(
      summary = "Hướng dẫn thanh toán và VietQR theo đơn; luôn có thông tin chuyển khoản dự phòng")
  public ApiResponse<PaymentInstructions> instructions(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId) {
    return ApiResponse.success(transactions.instructions(jwt.getSubject(), orderId));
  }

  public record ChangeMethod(@NotNull PaymentMethod paymentMethod) {}

  @PutMapping("/payment-method")
  @Operation(
      summary = "Đổi phương thức trên đơn chưa thanh toán")
  public ApiResponse<OrderResponse> change(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID orderId,
      @Valid @RequestBody ChangeMethod request) {
    return ApiResponse.success(
        transactions.changeMethod(jwt.getSubject(), orderId, request.paymentMethod()));
  }
}
