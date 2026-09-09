package com.lootfast.backend.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.slf4j.MDC;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        T data,
        ApiError error,
        String traceId,
        Instant timestamp
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, currentTraceId(), Instant.now());
    }

    // Dùng khi endpoint thành công nhưng không có data trả về (ví dụ: gửi OTP)
    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, null, currentTraceId(), Instant.now());
    }

    public static ApiResponse<Void> error(ApiError error) {
        return new ApiResponse<>(false, null, error, currentTraceId(), Instant.now());
    }

    // Đặt tên khác traceId() để tránh xung đột với record accessor
    private static String currentTraceId() {
        String id = MDC.get("traceId");
        return id != null ? id : "no-trace";
    }
}
