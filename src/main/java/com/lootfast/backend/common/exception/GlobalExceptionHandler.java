package com.lootfast.backend.common.exception;

import com.lootfast.backend.common.response.ApiError;
import com.lootfast.backend.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Mọi lỗi nghiệp vụ đều là ApiException — một handler duy nhất thay vì N handler riêng lẻ
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(
            ApiException ex,
            HttpServletRequest request
    ) {
        ApiError error = ApiError.of(ex.getErrorCode().name(), ex.getMessage());
        return ResponseEntity
                .status(ex.getErrorCode().getStatus())
                .body(ApiResponse.error(error));
    }

    // Lỗi @Valid — trả về field errors để FE map sang thông báo tiếng Việt
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> fields.putIfAbsent(fe.getField(), fe.getDefaultMessage()));

        ApiError error = ApiError.of(ErrorCode.VALIDATION_FAILED.name(),
                "Request validation failed", fields);
        return ResponseEntity
                .status(ErrorCode.VALIDATION_FAILED.getStatus())
                .body(ApiResponse.error(error));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableRequestBody(
            HttpMessageNotReadableException ex
    ) {
        ApiError error = ApiError.of(
                ErrorCode.VALIDATION_FAILED.name(),
                "Request body is missing or malformed"
        );
        return ResponseEntity
                .status(ErrorCode.VALIDATION_FAILED.getStatus())
                .body(ApiResponse.error(error));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException ex
    ) {
        ApiError error = ApiError.of(
                ErrorCode.UNSUPPORTED_MEDIA_TYPE.name(),
                "Content-Type must be application/json"
        );
        return ResponseEntity
                .status(ErrorCode.UNSUPPORTED_MEDIA_TYPE.getStatus())
                .body(ApiResponse.error(error));
    }

    // @PreAuthorize method-level ném AccessDeniedException — khác URL-level (do ApiSecurityErrorHandler xử lý)
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        ApiError error = ApiError.of(ErrorCode.ACCESS_DENIED.name(), "Access is denied");
        return ResponseEntity
                .status(ErrorCode.ACCESS_DENIED.getStatus())
                .body(ApiResponse.error(error));
    }

    // Catch-all — log đầy đủ cho dev, client chỉ nhận INTERNAL_ERROR (không lộ stack trace)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(
            Exception ex,
            HttpServletRequest request
    ) {
        log.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        ApiError error = ApiError.of(ErrorCode.INTERNAL_ERROR.name(),
                "An unexpected error occurred");
        return ResponseEntity
                .status(ErrorCode.INTERNAL_ERROR.getStatus())
                .body(ApiResponse.error(error));
    }
}
