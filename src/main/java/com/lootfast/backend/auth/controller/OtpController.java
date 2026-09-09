package com.lootfast.backend.auth.controller;

import com.lootfast.backend.auth.dto.ResendOtpRequest;
import com.lootfast.backend.auth.dto.VerifyOtpRequest;
import com.lootfast.backend.auth.service.OtpService;
import com.lootfast.backend.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "Authentication")
@RestController
@RequestMapping("/v1/auth")
public class OtpController {

    private final OtpService otpService;

    public OtpController(OtpService otpService) {
        this.otpService = otpService;
    }

    @Operation(summary = "Xác thực OTP",
            description = "Xác thực mã OTP 6 số gửi về email. Nhập sai 5 lần thì mã bị huỷ. TTL mã là 5 phút.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Xác thực thành công, tài khoản chuyển sang VERIFIED",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": { "message": "Email verified. You can now sign in." },
                                      "traceId": "abc-123",
                                      "timestamp": "2026-09-09T08:00:00Z"
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "OTP không đúng, đã hết hạn, hoặc field không hợp lệ",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = {
                                    @ExampleObject(name = "OTP_INVALID", summary = "Mã sai (còn lượt thử)",
                                            value = """
                                                    {
                                                      "success": false,
                                                      "error": {
                                                        "code": "OTP_INVALID",
                                                        "message": "Invalid OTP. 3 attempts remaining"
                                                      },
                                                      "traceId": "abc-123",
                                                      "timestamp": "2026-09-09T08:00:00Z"
                                                    }
                                                    """),
                                    @ExampleObject(name = "OTP_INVALID_LOCKED", summary = "Mã bị huỷ sau 5 lần sai",
                                            value = """
                                                    {
                                                      "success": false,
                                                      "error": {
                                                        "code": "OTP_INVALID",
                                                        "message": "Too many failed attempts. Please request a new OTP"
                                                      },
                                                      "traceId": "abc-123",
                                                      "timestamp": "2026-09-09T08:00:00Z"
                                                    }
                                                    """),
                                    @ExampleObject(name = "OTP_EXPIRED", summary = "Mã hết hạn sau 5 phút",
                                            value = """
                                                    {
                                                      "success": false,
                                                      "error": {
                                                        "code": "OTP_EXPIRED",
                                                        "message": "OTP has expired or does not exist"
                                                      },
                                                      "traceId": "abc-123",
                                                      "timestamp": "2026-09-09T08:00:00Z"
                                                    }
                                                    """)}))
    })
    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<Map<String, String>>> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest request
    ) {
        otpService.verifyOtp(request.email(), request.otp());
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("message", "Email verified. You can now sign in.")));
    }

    @Operation(summary = "Gửi lại OTP",
            description = "Gửi lại mã OTP mới. Giãn cách tối thiểu 60 giây giữa 2 lần gửi. Tối đa 3 lần trong 15 phút. Luôn trả 200 dù email có tồn tại hay không để tránh email enumeration.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Yêu cầu được tiếp nhận (email gửi nếu tài khoản tồn tại và chưa verify)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": { "message": "If the email exists and is unverified, a new OTP has been sent." },
                                      "traceId": "abc-123",
                                      "timestamp": "2026-09-09T08:00:00Z"
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "429",
                    description = "Gửi quá nhiều lần — cooldown 60s hoặc vượt 3 lần/15 phút",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = {
                                    @ExampleObject(name = "COOLDOWN", summary = "Chưa đủ 60 giây",
                                            value = """
                                                    {
                                                      "success": false,
                                                      "error": {
                                                        "code": "OTP_RATE_LIMIT",
                                                        "message": "Please wait 60 seconds before requesting a new OTP"
                                                      },
                                                      "traceId": "abc-123",
                                                      "timestamp": "2026-09-09T08:00:00Z"
                                                    }
                                                    """),
                                    @ExampleObject(name = "MAX_RESEND", summary = "Vượt quá 3 lần trong 15 phút",
                                            value = """
                                                    {
                                                      "success": false,
                                                      "error": {
                                                        "code": "OTP_RATE_LIMIT",
                                                        "message": "Maximum resend attempts reached. Please try again in 15 minutes"
                                                      },
                                                      "traceId": "abc-123",
                                                      "timestamp": "2026-09-09T08:00:00Z"
                                                    }
                                                    """)}))
    })
    @PostMapping("/resend-otp")
    public ResponseEntity<ApiResponse<Map<String, String>>> resendOtp(
            @Valid @RequestBody ResendOtpRequest request
    ) {
        otpService.resendOtp(request.email());
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("message", "If the email exists and is unverified, a new OTP has been sent.")));
    }
}
