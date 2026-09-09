package com.lootfast.backend.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    // --- Validation ---
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),

    // --- Auth ---
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT),
    DUPLICATE_PHONE(HttpStatus.CONFLICT),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    ACCOUNT_NOT_VERIFIED(HttpStatus.FORBIDDEN),

    // --- User ---
    USER_NOT_FOUND(HttpStatus.UNAUTHORIZED),

    // --- OTP (implement sau, khai báo trước để ErrorCode luôn complete) ---
    OTP_EXPIRED(HttpStatus.BAD_REQUEST),
    OTP_INVALID(HttpStatus.BAD_REQUEST),
    OTP_RATE_LIMIT(HttpStatus.TOO_MANY_REQUESTS),

    // --- Security (dùng bởi ApiSecurityErrorHandler) ---
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED(HttpStatus.FORBIDDEN),

    // --- System ---
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
