package com.lootfast.backend.auth.controller;

import com.lootfast.backend.auth.dto.LogoutRequest;
import com.lootfast.backend.auth.dto.RefreshTokenRequest;
import com.lootfast.backend.auth.dto.RefreshTokenResponse;
import com.lootfast.backend.auth.session.AuthSessionService;
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
@RequestMapping("/api/v1/auth")
public class AuthSessionController {

    private final AuthSessionService authSessionService;

    public AuthSessionController(AuthSessionService authSessionService) {
        this.authSessionService = authSessionService;
    }

    @Operation(
            summary = "Làm mới phiên đăng nhập",
            description = "Rotate refresh token hiện tại và trả về một cặp access/refresh token mới."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Làm mới token thành công",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
                                        "refreshToken": "550e8400-e29b-41d4-a716-446655440000.secret",
                                        "tokenType": "Bearer",
                                        "expiresIn": 900,
                                        "refreshExpiresIn": 2591900
                                      },
                                      "traceId": "abc-123",
                                      "timestamp": "2026-09-10T08:00:00Z"
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Refresh token không hợp lệ, đã hết hạn hoặc đã bị thu hồi"
            )
    })
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<RefreshTokenResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                authSessionService.refresh(request.refreshToken())
        ));
    }

    @Operation(
            summary = "Đăng xuất thiết bị hiện tại",
            description = "Thu hồi refresh session hiện tại. Endpoint có tính idempotent."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "204",
            description = "Đăng xuất hoàn tất"
    )
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authSessionService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
