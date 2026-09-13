package com.lootfast.backend.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    // --- Validation ---
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),

    // --- Auth ---
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT),
    DUPLICATE_PHONE(HttpStatus.CONFLICT),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    ACCOUNT_NOT_VERIFIED(HttpStatus.FORBIDDEN),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED),

    // --- User ---
    USER_NOT_FOUND(HttpStatus.UNAUTHORIZED),
    INVALID_AVATAR(HttpStatus.BAD_REQUEST),
    AVATAR_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
    AVATAR_UPLOAD_FAILED(HttpStatus.BAD_GATEWAY),

    // --- Merchant ---
    MERCHANT_PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND),
    INVALID_MERCHANT_IMAGE(HttpStatus.BAD_REQUEST),
    MERCHANT_IMAGE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
    MERCHANT_IMAGE_UPLOAD_FAILED(HttpStatus.BAD_GATEWAY),
    MERCHANT_APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND),
    MERCHANT_APPLICATION_ALREADY_PENDING(HttpStatus.CONFLICT),
    MERCHANT_APPLICATION_NOT_PENDING(HttpStatus.CONFLICT),
    MERCHANT_ALREADY_APPROVED(HttpStatus.CONFLICT),
    INVALID_LICENSE_DOCUMENT(HttpStatus.BAD_REQUEST),
    LICENSE_DOCUMENT_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
    LICENSE_UPLOAD_FAILED(HttpStatus.BAD_GATEWAY),
    LICENSE_ACCESS_FAILED(HttpStatus.BAD_GATEWAY),

    // --- OTP ---
    OTP_EXPIRED(HttpStatus.BAD_REQUEST),
    OTP_INVALID(HttpStatus.BAD_REQUEST),

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
