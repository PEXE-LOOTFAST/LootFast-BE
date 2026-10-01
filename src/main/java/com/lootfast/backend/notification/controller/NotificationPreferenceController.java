package com.lootfast.backend.notification.controller;

import com.lootfast.backend.common.response.ApiResponse;
import com.lootfast.backend.notification.dto.NotificationPreferenceResponse;
import com.lootfast.backend.notification.dto.UpdateNotificationPreferenceRequest;
import com.lootfast.backend.notification.service.NotificationPreferenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/notification-preferences")
@Tag(name = "Notification preferences")
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
public class NotificationPreferenceController {

    private final NotificationPreferenceService preferenceService;

    public NotificationPreferenceController(NotificationPreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    @GetMapping
    @Operation(summary = "Lấy cài đặt thông báo của tài khoản hiện tại")
    public ApiResponse<NotificationPreferenceResponse> get(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.success(preferenceService.get(jwt.getSubject()));
    }

    @PutMapping
    @Operation(summary = "Cập nhật toàn bộ cài đặt thông báo của tài khoản hiện tại")
    public ApiResponse<NotificationPreferenceResponse> update(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateNotificationPreferenceRequest request
    ) {
        return ApiResponse.success(preferenceService.update(jwt.getSubject(), request));
    }
}
