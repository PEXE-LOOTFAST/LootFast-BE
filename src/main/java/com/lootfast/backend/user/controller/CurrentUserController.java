package com.lootfast.backend.user.controller;

import com.lootfast.backend.common.response.ApiResponse;
import com.lootfast.backend.user.dto.CurrentUserResponse;
import com.lootfast.backend.user.service.CurrentUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "User", description = "Thông tin tài khoản người dùng hiện tại")
@RestController
@RequestMapping("/api/v1/users")
public class CurrentUserController {

    private final CurrentUserService currentUserService;

    public CurrentUserController(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    @Operation(summary = "Lấy thông tin tài khoản hiện tại",
            description = "Trả về profile của user đang đăng nhập dựa trên JWT. Yêu cầu Bearer token hợp lệ.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Thành công",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "publicId": "550e8400-e29b-41d4-a716-446655440000",
                                        "firstName": "Kien",
                                        "lastName": "Nguyen Van",
                                        "email": "kien@gmail.com",
                                        "phoneNumber": "912345678",
                                        "role": "BUYER",
                                        "createdAt": "2026-09-09T08:00:00Z"
                                      },
                                      "traceId": "abc-123",
                                      "timestamp": "2026-09-09T08:00:00Z"
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Không có token hoặc token không hợp lệ / user đã bị xoá",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = {
                                    @ExampleObject(name = "UNAUTHORIZED", summary = "Không có token",
                                            value = """
                                                    {
                                                      "success": false,
                                                      "error": {
                                                        "code": "UNAUTHORIZED",
                                                        "message": "Authentication is required"
                                                      },
                                                      "traceId": "abc-123",
                                                      "timestamp": "2026-09-09T08:00:00Z"
                                                    }
                                                    """),
                                    @ExampleObject(name = "USER_NOT_FOUND", summary = "User đã bị xoá khỏi DB",
                                            value = """
                                                    {
                                                      "success": false,
                                                      "error": {
                                                        "code": "USER_NOT_FOUND",
                                                        "message": "Authenticated user is no longer available"
                                                      },
                                                      "traceId": "abc-123",
                                                      "timestamp": "2026-09-09T08:00:00Z"
                                                    }
                                                    """)}))
    })
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CurrentUserResponse>> getCurrentUser(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                currentUserService.getCurrentUser(jwt.getSubject())));
    }
}
