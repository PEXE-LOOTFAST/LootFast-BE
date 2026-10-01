package com.lootfast.backend.order.controller;

import com.lootfast.backend.common.response.ApiResponse;
import com.lootfast.backend.common.response.PagedResponse;
import com.lootfast.backend.order.dto.ConfirmPickupRequest;
import com.lootfast.backend.order.dto.MerchantOrderResponse;
import com.lootfast.backend.order.entity.OrderStatus;
import com.lootfast.backend.order.service.MerchantOrderService;
import com.lootfast.backend.payment.dto.RejectPaymentRequest;
import com.lootfast.backend.payment.service.MerchantPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/merchants/me/orders")
@Tag(name = "Merchant orders")
@PreAuthorize("hasRole('MERCHANT')")
@SecurityRequirement(name = "bearerAuth")
public class MerchantOrderController {
  private final MerchantOrderService service;
  private final MerchantPaymentService payments;

  public MerchantOrderController(
      MerchantOrderService service, MerchantPaymentService payments) {
    this.service = service;
    this.payments = payments;
  }

  @GetMapping
  @Operation(summary = "Xem đơn của cửa hàng theo khung giờ nhận, trạng thái và phân trang")
  public ApiResponse<PagedResponse<MerchantOrderResponse>> list(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam Instant pickupFrom,
      @RequestParam Instant pickupTo,
      @RequestParam(required = false) OrderStatus status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.success(
        service.list(jwt.getSubject(), pickupFrom, pickupTo, status, page, size));
  }

  @PostMapping("/pickup-confirmations")
  @Operation(summary = "Xác nhận giao đơn đúng một lần bằng mã LF hoặc payload QR")
  public ApiResponse<MerchantOrderResponse> confirm(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ConfirmPickupRequest request) {
    return ApiResponse.success(service.confirm(jwt.getSubject(), request.credential()));
  }

  @PostMapping("/{orderId}/payment-confirmation")
  @Operation(summary = "Xác nhận cửa hàng đã nhận đúng tiền chuyển khoản")
  public ApiResponse<MerchantOrderResponse> confirmPayment(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId) {
    return ApiResponse.success(payments.confirmBankTransfer(jwt.getSubject(), orderId));
  }

  @PostMapping("/{orderId}/payment-rejection")
  @Operation(summary = "Từ chối báo cáo chuyển khoản và yêu cầu người mua kiểm tra lại")
  public ApiResponse<MerchantOrderResponse> rejectPayment(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID orderId,
      @Valid @RequestBody RejectPaymentRequest request) {
    return ApiResponse.success(payments.rejectBankTransfer(jwt.getSubject(), orderId, request));
  }
}
