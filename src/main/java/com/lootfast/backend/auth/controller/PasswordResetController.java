package com.lootfast.backend.auth.controller;

import com.lootfast.backend.auth.dto.PasswordResetConfirmRequest;
import com.lootfast.backend.auth.dto.PasswordResetRequest;
import com.lootfast.backend.auth.password.PasswordResetService;
import com.lootfast.backend.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "Authentication")
@RestController
@RequestMapping("/api/v1/auth/password-reset")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @Operation(
            summary = "Yêu cầu OTP đặt lại mật khẩu",
            description = "Phản hồi giống nhau dù email có tồn tại hay không; tối đa 3 yêu cầu trong 15 phút và cách nhau ít nhất 60 giây."
    )
    @PostMapping("/request")
    public ResponseEntity<ApiResponse<Map<String, String>>> request(
            @Valid @RequestBody PasswordResetRequest request
    ) {
        passwordResetService.requestReset(request.email());
        return ResponseEntity.accepted().body(ApiResponse.success(Map.of(
                "message", "If the account exists, a password reset OTP has been sent."
        )));
    }

    @Operation(
            summary = "Xác nhận OTP và đặt mật khẩu mới",
            description = "OTP chỉ dùng một lần. Sau khi đổi mật khẩu thành công, toàn bộ refresh token đang hoạt động bị thu hồi."
    )
    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<Map<String, String>>> confirm(
            @Valid @RequestBody PasswordResetConfirmRequest request
    ) {
        passwordResetService.confirmReset(request);
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "message", "Password has been reset. Please sign in again."
        )));
    }
}
