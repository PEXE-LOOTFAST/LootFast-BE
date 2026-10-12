package com.lootfast.backend.common.exception;

import com.lootfast.backend.common.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTests {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsDuplicatePhoneConstraintToConflict() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "could not execute statement",
                new RuntimeException(
                        "ERROR: duplicate key value violates unique constraint "
                                + "\"uk_users_phone_number\""));

        ResponseEntity<ApiResponse<Void>> response =
                handler.handleDataIntegrityViolation(ex, new MockHttpServletRequest());

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().success());
        assertEquals(ErrorCode.DUPLICATE_PHONE.name(), response.getBody().error().code());
    }

    @Test
    void mapsDuplicateEmailConstraintToConflict() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "could not execute statement",
                new RuntimeException(
                        "ERROR: duplicate key value violates unique constraint "
                                + "\"uk_users_email\""));

        ResponseEntity<ApiResponse<Void>> response =
                handler.handleDataIntegrityViolation(ex, new MockHttpServletRequest());

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(ErrorCode.DUPLICATE_EMAIL.name(), response.getBody().error().code());
    }

    @Test
    void mapsUnknownConstraintToGenericConflictNotInternalError() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "ERROR: violates foreign key constraint \"fk_some_other_table\"");

        ResponseEntity<ApiResponse<Void>> response =
                handler.handleDataIntegrityViolation(ex, new MockHttpServletRequest());

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(ErrorCode.CONTENT_CONFLICT.name(), response.getBody().error().code());
    }
}
