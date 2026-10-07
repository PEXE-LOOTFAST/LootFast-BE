package com.lootfast.backend.premium.vnpay.query;

import com.lootfast.backend.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/premium/orders")
@SecurityRequirement(name = "bearerAuth")
public class QueryController {
  private final QueryStore store;
  private final QuerySettings settings;

  public QueryController(QueryStore store, QuerySettings settings) {
    this.store = store;
    this.settings = settings;
  }

  @PostMapping("/{id}/reconciliation")
  @Operation(
      summary = "Yêu cầu kiểm tra lại đơn Premium của mình",
      description =
          "HTTP 202: đưa vào worker, không chứng minh thanh toán thành công. Chờ ít nhất 5 phút từ"
              + " attempt/lần tra cứu; kết quả đọc qua GET order.")
  public ResponseEntity<ApiResponse<QueryStore.Status>> request(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    settings.requireEnabled();
    return ResponseEntity.accepted().body(ApiResponse.success(store.request(jwt.getSubject(), id)));
  }

  @GetMapping("/{id}/reconciliation")
  @Operation(
      summary = "Xem trạng thái đối soát đơn của mình",
      description =
          "WAITING/PROCESSING/DONE/EXHAUSTED/MANUAL_REVIEW; payment success vẫn đọc ở GET order.")
  public ApiResponse<QueryStore.Status> status(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return ApiResponse.success(store.ownedStatus(jwt.getSubject(), id));
  }
}
