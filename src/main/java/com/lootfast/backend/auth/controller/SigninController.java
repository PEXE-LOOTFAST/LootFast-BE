package com.lootfast.backend.auth.controller;

import com.lootfast.backend.auth.dto.SigninRequest;
import com.lootfast.backend.auth.dto.SigninResponse;
import com.lootfast.backend.auth.service.SigninService;
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

@Tag(name = "Authentication")
@RestController
@RequestMapping("/v1/auth")
public class SigninController {

    private final SigninService signinService;

    public SigninController(SigninService signinService) {
        this.signinService = signinService;
    }

    @Operation(summary = "Đăng nhập",
            description = "Trả về JWT access token. Tài khoản phải ở trạng thái VERIFIED mới đăng nhập được.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Đăng nhập thành công",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
                                        "tokenType": "Bearer",
                                        "expiresIn": 900,
                                        "firstName": "Kien",
                                        "lastName": "Nguyen Van",
                                        "email": "kien@gmail.com",
                                        "role": "BUYER"
                                      },
                                      "traceId": "abc-123",
                                      "timestamp": "2026-09-09T08:00:00Z"
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Field không hợp lệ",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(name = "VALIDATION_FAILED",
                                    value = """
                                            {
                                              "success": false,
                                              "error": {
                                                "code": "VALIDATION_FAILED",
                                                "message": "Request validation failed",
                                                "fields": { "email": "must be a well-formed email address" }
                                              },
                                              "traceId": "abc-123",
                                              "timestamp": "2026-09-09T08:00:00Z"
                                            }
                                            """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Email hoặc mật khẩu không đúng",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(name = "INVALID_CREDENTIALS",
                                    value = """
                                            {
                                              "success": false,
                                              "error": {
                                                "code": "INVALID_CREDENTIALS",
                                                "message": "Invalid email or password"
                                              },
                                              "traceId": "abc-123",
                                              "timestamp": "2026-09-09T08:00:00Z"
                                            }
                                            """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Tài khoản chưa xác thực email",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(name = "ACCOUNT_NOT_VERIFIED",
                                    value = """
                                            {
                                              "success": false,
                                              "error": {
                                                "code": "ACCOUNT_NOT_VERIFIED",
                                                "message": "Please verify your email before signing in"
                                              },
                                              "traceId": "abc-123",
                                              "timestamp": "2026-09-09T08:00:00Z"
                                            }
                                            """)))
    })
    @PostMapping("/signin")
    public ResponseEntity<ApiResponse<SigninResponse>> signin(
            @Valid @RequestBody SigninRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(signinService.signin(request)));
    }
}
