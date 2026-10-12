package com.lootfast.backend.common.exception;

import com.lootfast.backend.common.response.ApiError;
import com.lootfast.backend.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

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
                "Request Content-Type is not supported by this endpoint"
        );
        return ResponseEntity
                .status(ErrorCode.UNSUPPORTED_MEDIA_TYPE.getStatus())
                .body(ApiResponse.error(error));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(
            MaxUploadSizeExceededException exception,
            HttpServletRequest request
    ) {
        if (request.getServletPath().matches("/api/v1/admin/banners/[^/]+/image")) {
            return ResponseEntity.status(ErrorCode.BANNER_IMAGE_TOO_LARGE.getStatus()).body(ApiResponse.error(
                    ApiError.of("BANNER_IMAGE_TOO_LARGE", "Banner image must not exceed 5 MB")));
        }
        boolean licenseDocument = isLicenseDocumentRequest(request);
        boolean dealImage = isDealImageRequest(request);
        boolean merchantImage = isMerchantImageRequest(request);
        ErrorCode errorCode = licenseDocument
                ? ErrorCode.LICENSE_DOCUMENT_TOO_LARGE
                : dealImage
                        ? ErrorCode.DEAL_IMAGE_TOO_LARGE
                : merchantImage
                        ? ErrorCode.MERCHANT_IMAGE_TOO_LARGE
                        : ErrorCode.AVATAR_TOO_LARGE;
        ApiError error = ApiError.of(
                errorCode.name(),
                licenseDocument
                        ? "Business license image must not exceed 5 MB"
                        : dealImage
                                ? "Deal image file must not exceed 5 MB"
                        : merchantImage
                                ? "Merchant image file must not exceed 5 MB"
                                : "Avatar file must not exceed 5 MB"
        );
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.error(error));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingRequestPart(
            MissingServletRequestPartException exception,
            HttpServletRequest request
    ) {
        if (request.getServletPath().matches("/api/v1/admin/banners/[^/]+/image")) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ApiError.of(
                    "INVALID_BANNER_IMAGE", "Banner image file is required")));
        }
        boolean licenseDocument = isLicenseDocumentRequest(request);
        boolean dealImage = isDealImageRequest(request);
        boolean merchantImage = isMerchantImageRequest(request);
        ErrorCode errorCode = licenseDocument
                ? ErrorCode.INVALID_LICENSE_DOCUMENT
                : dealImage
                        ? ErrorCode.INVALID_DEAL_IMAGE
                : merchantImage
                        ? ErrorCode.INVALID_MERCHANT_IMAGE
                        : ErrorCode.INVALID_AVATAR;
        ApiError error = ApiError.of(
                errorCode.name(),
                licenseDocument
                        ? "Business license number and image are required"
                        : dealImage
                                ? "Deal image file is required"
                        : merchantImage
                                ? "Merchant image file is required"
                                : "Avatar file is required"
        );
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.error(error));
    }

    private boolean isMerchantImageRequest(HttpServletRequest request) {
        return request.getServletPath().startsWith("/api/v1/merchants/");
    }

    private boolean isDealImageRequest(HttpServletRequest request) {
        return request.getServletPath().matches("/api/v1/merchants/me/deals/[^/]+/images.*");
    }

    private boolean isLicenseDocumentRequest(HttpServletRequest request) {
        return request.getServletPath().startsWith("/api/v1/merchant-applications");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
            ConstraintViolationException exception
    ) {
        ApiError error = ApiError.of(
                ErrorCode.VALIDATION_FAILED.name(),
                "Request validation failed"
        );
        return ResponseEntity
                .status(ErrorCode.VALIDATION_FAILED.getStatus())
                .body(ApiResponse.error(error));
    }

    @ExceptionHandler({
            HandlerMethodValidationException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            org.springframework.web.bind.MissingRequestHeaderException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleRequestParameterValidation(
            Exception exception
    ) {
        ApiError error = ApiError.of(
                ErrorCode.VALIDATION_FAILED.name(),
                "Request parameter validation failed"
        );
        return ResponseEntity
                .status(ErrorCode.VALIDATION_FAILED.getStatus())
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

    @ExceptionHandler({org.springframework.dao.PessimisticLockingFailureException.class,
            org.springframework.dao.QueryTimeoutException.class})
    public ResponseEntity<ApiResponse<Void>> handleBusy(Exception ex) {
        return ResponseEntity.status(ErrorCode.RESOURCE_BUSY.getStatus())
            .body(ApiResponse.error(ApiError.of("RESOURCE_BUSY", "Resource is busy; retry with the same key")));
    }

    // Vi phạm ràng buộc dữ liệu (unique...) — thường do race giữa pre-check và commit.
    // Map về 409 theo tên constraint thay vì để rơi vào catch-all INTERNAL_ERROR (500).
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(
            org.springframework.dao.DataIntegrityViolationException ex,
            HttpServletRequest request
    ) {
        String details = constraintDetails(ex);
        ErrorCode errorCode;
        String message;
        if (details.contains("uk_users_email")) {
            errorCode = ErrorCode.DUPLICATE_EMAIL;
            message = "Email is already registered";
        } else if (details.contains("uk_users_phone_number")) {
            errorCode = ErrorCode.DUPLICATE_PHONE;
            message = "Phone number is already registered";
        } else {
            // Ràng buộc khác chưa map riêng: vẫn trả 409 (không lộ 500) và log để điều tra.
            log.warn("Unmapped data integrity violation on {} {}",
                    request.getMethod(), request.getRequestURI(), ex);
            errorCode = ErrorCode.CONTENT_CONFLICT;
            message = "Request conflicts with existing data";
        }
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.error(ApiError.of(errorCode.name(), message)));
    }

    // Gom message toàn bộ chuỗi cause để dò tên constraint (Postgres nhét tên vào message lỗi).
    private String constraintDetails(Throwable ex) {
        StringBuilder details = new StringBuilder();
        Throwable cause = ex;
        for (int depth = 0; cause != null && depth < 10; depth++) {
            if (cause.getMessage() != null) {
                details.append(cause.getMessage()).append(' ');
            }
            cause = cause.getCause();
        }
        return details.toString().toLowerCase(java.util.Locale.ROOT);
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
