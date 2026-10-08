package com.lootfast.backend.notification.controller;

import com.lootfast.backend.common.response.ApiResponse;
import com.lootfast.backend.common.response.PagedResponse;
import com.lootfast.backend.notification.dto.NotificationResponse;
import com.lootfast.backend.notification.dto.ReadAllNotificationsResponse;
import com.lootfast.backend.notification.dto.UnreadNotificationCountResponse;
import com.lootfast.backend.notification.service.UserNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications")
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
public class UserNotificationController {

    private final UserNotificationService notificationService;

    public UserNotificationController(UserNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @Operation(summary = "Danh sách thông báo của tài khoản hiện tại")
    public ApiResponse<PagedResponse<NotificationResponse>> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(notificationService.list(jwt.getSubject(), page, size));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Đếm thông báo chưa đọc")
    public ApiResponse<UnreadNotificationCountResponse> unreadCount(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.success(notificationService.unreadCount(jwt.getSubject()));
    }

    @PutMapping("/{notificationId}/read")
    @Operation(summary = "Đánh dấu một thông báo đã đọc")
    public ApiResponse<NotificationResponse> markRead(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID notificationId
    ) {
        return ApiResponse.success(notificationService.markRead(jwt.getSubject(), notificationId));
    }

    @PutMapping("/read-all")
    @Operation(summary = "Đánh dấu tất cả thông báo đã đọc")
    public ApiResponse<ReadAllNotificationsResponse> markAllRead(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.success(notificationService.markAllRead(jwt.getSubject()));
    }
}
