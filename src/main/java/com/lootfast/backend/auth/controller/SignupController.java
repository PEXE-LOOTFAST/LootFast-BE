package com.lootfast.backend.auth.controller;

import com.lootfast.backend.auth.dto.SignupRequest;
import com.lootfast.backend.auth.dto.SignupResponse;
import com.lootfast.backend.auth.service.SignupService;
import com.lootfast.backend.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Authentication", description = "Đăng ký, đăng nhập, xác thực OTP")
@RestController
@RequestMapping("/api/v1/auth")
public class SignupController {

    private final SignupService signupService;

    public SignupController(SignupService signupService) {
        this.signupService = signupService;
    }

    @Operation(summary = "Đăng ký tài khoản",
            description = "Tạo tài khoản mới, ghi nhận phiên bản điều khoản/chính sách đã đồng ý và gửi OTP email. Chọn MERCHANT chỉ định tuyến sang hồ sơ xét duyệt, không tự cấp quyền người bán.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "Đăng ký thành công, OTP đã gửi về email",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "message": "Verification code sent to kien@gmail.com",
                                        "email": "kien@gmail.com",
                                        "accountType": "BUYER",
                                        "nextAction": "BUYER_HOME"
                                      },
                                      "traceId": "abc-123",
                                      "timestamp": "2026-09-09T08:00:00Z"
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Lỗi validation hoặc mật khẩu không khớp",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = {
                                    @ExampleObject(name = "VALIDATION_FAILED", summary = "Field không hợp lệ",
                                            value = """
                                                    {
                                                      "success": false,
                                                      "error": {
                                                        "code": "VALIDATION_FAILED",
                                                        "message": "Request validation failed",
                                                        "fields": {
                                                          "email": "Email must be a Gmail address",
                                                          "phoneNumber": "Phone number must contain exactly 9 digits"
                                                        }
                                                      },
                                                      "traceId": "abc-123",
                                                      "timestamp": "2026-09-09T08:00:00Z"
                                                    }
                                                    """),
                                    @ExampleObject(name = "PASSWORD_MISMATCH", summary = "Mật khẩu xác nhận không khớp",
                                            value = """
                                                    {
                                                      "success": false,
                                                      "error": {
                                                        "code": "PASSWORD_MISMATCH",
                                                        "message": "Password confirmation does not match"
                                                      },
                                                      "traceId": "abc-123",
                                                      "timestamp": "2026-09-09T08:00:00Z"
                                                    }
                                                    """)})),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "Email hoặc số điện thoại đã được đăng ký",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = {
                                    @ExampleObject(name = "DUPLICATE_EMAIL", summary = "Email đã tồn tại",
                                            value = """
                                                    {
                                                      "success": false,
                                                      "error": {
                                                        "code": "DUPLICATE_EMAIL",
                                                        "message": "Email is already registered"
                                                      },
                                                      "traceId": "abc-123",
                                                      "timestamp": "2026-09-09T08:00:00Z"
                                                    }
                                                    """),
                                    @ExampleObject(name = "DUPLICATE_PHONE", summary = "Số điện thoại đã tồn tại",
                                            value = """
                                                    {
                                                      "success": false,
                                                      "error": {
                                                        "code": "DUPLICATE_PHONE",
                                                        "message": "Phone number is already registered"
                                                      },
                                                      "traceId": "abc-123",
                                                      "timestamp": "2026-09-09T08:00:00Z"
                                                    }
                                                    """)}))
    })
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(
            @Valid @RequestBody SignupRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(signupService.signup(request)));
    }
}
