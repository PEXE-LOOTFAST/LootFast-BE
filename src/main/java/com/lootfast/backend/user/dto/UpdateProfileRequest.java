package com.lootfast.backend.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cập nhật thông tin cơ bản của tài khoản đang đăng nhập (partial update).
 *
 * <p>Mọi field đều tùy chọn: field để null (hoặc không gửi) sẽ giữ nguyên giá trị cũ,
 * chỉ field có giá trị mới được cập nhật. Chuỗi rỗng/khoảng trắng được coi như không đổi.
 *
 * <p>Không đổi email ở đây — email là định danh đăng nhập, cần luồng xác thực OTP riêng.
 * Số điện thoại chỉ kiểm tra trùng, không xác thực OTP (nhất quán với luồng đăng ký).
 */
public record UpdateProfileRequest(
        @Size(max = 100, message = "First name must not exceed 100 characters")
        String firstName,

        @Size(max = 100, message = "Last name must not exceed 100 characters")
        String lastName,

        // @Pattern cho phép null (bỏ qua); chỉ kiểm tra khi có giá trị.
        @Pattern(regexp = "^\\d{9}$", message = "Phone number must contain exactly 9 digits")
        String phoneNumber,

        @Size(max = 100, message = "Display name must not exceed 100 characters")
        String displayName
) {
}
