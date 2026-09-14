package com.lootfast.backend.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        String code,
        String message,
        Map<String, String> fields  // chỉ có khi VALIDATION_FAILED, null các trường hợp khác
) {
    public static ApiError of(String code, String message) {
        return new ApiError(code, message, null);
    }

    public static ApiError of(String code, String message, Map<String, String> fields) {
        return new ApiError(code, message, fields);
    }
}
