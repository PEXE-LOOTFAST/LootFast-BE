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
@RequestMapping("/api/v1/auth")
public class OtpController {

    private final OtpService otpService;

    public OtpController(OtpService otpService) {
        this.otpService = otpService;
    }

    @Operation(
            summary = "Xác thực OTP",
            description = "Xác thực OTP 6 số gửi qua email. Mã hết hạn sau 5 phút và bị hủy sau 5 lần nhập sai."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Email được xác thực thành công",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {"message": "Email verified. You can now sign in."},
                                      "traceId": "abc-123",
                                      "timestamp": "2026-09-09T08:00:00Z"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "OTP không đúng, đã hết hạn hoặc request không hợp lệ"
            )
    })
    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<Map<String, String>>> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest request
    ) {
        otpService.verifyOtp(request.email(), request.otp());
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("message", "Email verified. You can now sign in.")));
    }

    @Operation(
            summary = "Gửi lại OTP",
            description = "Giãn cách tối thiểu 60 giây và tối đa 3 lần trong 15 phút. Rate limit được áp dụng âm thầm; endpoint luôn trả 200 để không làm lộ trạng thái tài khoản."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Yêu cầu được tiếp nhận"
    )
    @PostMapping("/resend-otp")
    public ResponseEntity<ApiResponse<Map<String, String>>> resendOtp(
            @Valid @RequestBody ResendOtpRequest request
    ) {
        otpService.resendOtp(request.email());
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("message", "If the email exists and is unverified, a new OTP has been sent.")));
    }
}
