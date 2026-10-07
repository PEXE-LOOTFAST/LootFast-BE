package com.lootfast.backend.premium.subscription.controller;

import com.lootfast.backend.common.response.*;
import com.lootfast.backend.premium.catalog.dto.PlanAudience;
import com.lootfast.backend.premium.subscription.dto.SubscriptionResponse;
import com.lootfast.backend.premium.subscription.dto.SubscriptionResponse.Period;
import com.lootfast.backend.premium.subscription.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/premium/me")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Premium subscriptions")
public class SubscriptionController {
  private final SubscriptionService service;

  public SubscriptionController(SubscriptionService service) {
    this.service = service;
  }

  @GetMapping("/subscriptions")
  @Operation(
      summary = "Xem subscription trả phí của mình theo hai phía",
      description =
          "NONE không có kỳ trả phí; ACTIVE/EXPIRED/SUSPENDED theo thời gian và điều kiện tài"
              + " khoản. Quyền MANUAL vẫn hiển thị riêng trong benefits, không được coi là đã mua."
              + " Chưa có checkout ở bước này.")
  public ApiResponse<List<SubscriptionResponse>> list(@AuthenticationPrincipal Jwt jwt) {
    return ApiResponse.success(service.list(jwt.getSubject()));
  }

  @GetMapping("/benefits")
  @Operation(
      summary = "Quyền thực tế và nguồn MANUAL/PURCHASE của mình",
      description =
          "effectiveUntil gồm các khoảng quyền liên tục; gap không được cộng. enabled còn phụ thuộc"
              + " role, xác minh, khóa tài khoản và duyệt merchant. Thời gian lấy từ database,"
              + " không từ cờ JWT.")
  public ApiResponse<SubscriptionResponse> benefits(
      @AuthenticationPrincipal Jwt jwt, @RequestParam PlanAudience audience) {
    return ApiResponse.success(service.benefits(jwt.getSubject(), audience));
  }

  @GetMapping("/subscriptions/{audience}/periods")
  @Operation(
      summary = "Lịch sử kỳ trả phí của mình; size 1–50",
      description =
          "Giữ snapshot phiên bản gói. Không nhận userId từ client; không trả lịch sử người khác."
              + " Admin cũng chỉ xem của mình ở API này.")
  public ApiResponse<PagedResponse<Period>> periods(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable PlanAudience audience,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.success(service.periods(jwt.getSubject(), audience, page, size));
  }
}
