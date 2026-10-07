package com.lootfast.backend.content.controller;

import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.common.response.*;
import java.sql.SQLException;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes = AdminContentController.class)
@Order(0)
public class ContentExceptionHandler {
  @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
  public ResponseEntity<ApiResponse<Void>> staleEdit(Exception exception) {
    return ResponseEntity.status(ErrorCode.CONTENT_CONFLICT.getStatus())
        .body(
            ApiResponse.error(
                ApiError.of("CONTENT_CONFLICT", "Content changed; reload before editing")));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ApiResponse<Void>> conflict(DataIntegrityViolationException exception) {
    for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
      if (cause instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
        return ResponseEntity.status(ErrorCode.CONTENT_CONFLICT.getStatus())
            .body(
                ApiResponse.error(
                    ApiError.of(
                        "CONTENT_CONFLICT", "Category code or document version already exists")));
      }
    }
    LoggerFactory.getLogger(getClass()).error("Unexpected content integrity error", exception);
    return ResponseEntity.internalServerError()
        .body(ApiResponse.error(ApiError.of("INTERNAL_ERROR", "An unexpected error occurred")));
  }
}
