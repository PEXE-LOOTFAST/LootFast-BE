package com.lootfast.backend.premium.vnpay;

import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.response.ApiResponse;
import io.micrometer.core.instrument.MeterRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.slf4j.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments/vnpay")
@Tag(name = "VNPay callbacks")
public class VnpayController {
  private final VnpayVerifier verifier;
  private final VnpayIpnService service;
  private final VnpayReturnDestination destination;
  private final MeterRegistry metrics;
  private static final Logger log = LoggerFactory.getLogger(VnpayController.class);

  public VnpayController(
      VnpayVerifier verifier,
      VnpayIpnService service,
      VnpayReturnDestination destination,
      MeterRegistry metrics) {
    this.verifier = verifier;
    this.service = service;
    this.destination = destination;
    this.metrics = metrics;
  }

  private VnpayNotification verify(HttpServletRequest r) {
    int length = r.getQueryString() == null ? 0 : r.getQueryString().length();
    if (length > 8192) throw new InvalidVnpayNotification("99");
    return verifier.verify(r.getParameterMap(), length);
  }

  @GetMapping("/ipn")
  @Operation(
      summary = "VNPay server IPN; HMAC-SHA512, không dùng JWT",
      description =
          "HTTP 200 + RspCode/Message theo VNPay. Xác nhận chỉ sau commit. Không gọi từ frontend để"
              + " giả báo thanh toán.")
  public VnpayAck ipn(HttpServletRequest request) {
    VnpayAck ack;
    try {
      ack = service.process(verify(request));
    } catch (InvalidVnpayNotification e) {
      ack = VnpayAck.of(e.code());
    } catch (ApiException e) {
      ack = VnpayAck.of("99");
    } catch (RuntimeException e) {
      log.warn("VNPay IPN processing failed; exceptionType={}", e.getClass().getSimpleName());
      ack = VnpayAck.of("99");
    }
    metrics.counter("lootfast.vnpay.ipn", "result", ack.code()).increment();
    return ack;
  }

  public record ReturnResult(UUID orderId, String status) {}

  @GetMapping("/return")
  @Operation(
      summary = "VNPay browser return; không ghi thanh toán/cấp quyền",
      description =
          "Redirect URL FE cố định với orderId; FE đăng nhập rồi GET order owned. Nếu FE URL chưa"
              + " cấu hình thì trả JSON AWAITING_CONFIRMATION.")
  public ResponseEntity<?> browserReturn(HttpServletRequest request) {
    UUID order = null;
    boolean valid = false;
    try {
      order = service.returnOrder(verify(request));
      valid = true;
    } catch (InvalidVnpayNotification e) {
      /* Fixed error response; never forward raw provider fields. */
    }
    var location = destination.result(order, valid);
    var headers = new HttpHeaders();
    headers.setCacheControl("no-store");
    headers.set("Referrer-Policy", "no-referrer");
    if (location != null) {
      headers.setLocation(location);
      return new ResponseEntity<>(null, headers, HttpStatus.SEE_OTHER);
    }
    return new ResponseEntity<>(
        ApiResponse.success(
            new ReturnResult(order, valid ? "AWAITING_CONFIRMATION" : "INVALID_CALLBACK")),
        headers,
        valid ? HttpStatus.OK : HttpStatus.BAD_REQUEST);
  }
}
