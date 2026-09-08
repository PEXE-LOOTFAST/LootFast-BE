package com.lootfast.backend.auth.controller;

import com.lootfast.backend.auth.dto.SignupResponse;
import com.lootfast.backend.auth.exception.DuplicateUserException;
import com.lootfast.backend.auth.exception.PasswordMismatchException;
import com.lootfast.backend.auth.service.SignupService;
import com.lootfast.backend.common.exception.GlobalExceptionHandler;
import com.lootfast.backend.user.entity.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class SignupControllerTests {

    private SignupService signupService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        signupService = mock(SignupService.class);
        mockMvc = standaloneSetup(new SignupController(signupService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void signupReturnsCreatedUser() throws Exception {
        SignupResponse response = new SignupResponse(
                1L,
                "Kien",
                "Nguyen Van",
                "kien@gmail.com",
                "912345678",
                UserRole.BUYER,
                Instant.parse("2026-09-06T08:00:00Z")
        );
        when(signupService.signup(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("kien@gmail.com"))
                .andExpect(jsonPath("$.role").value("BUYER"));
    }

    @Test
    void signupReturnsBadRequestForInvalidFields() throws Exception {
        String invalidRequest = """
                {
                  "firstName": "Kien",
                  "lastName": "Nguyen Van",
                  "email": "kien@yahoo.com",
                  "phoneNumber": "123",
                  "password": "password123",
                  "confirmPassword": "password123"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.validationErrors.email").exists())
                .andExpect(jsonPath("$.validationErrors.phoneNumber").exists());
    }

    @Test
    void signupReturnsConflictForDuplicateUser() throws Exception {
        when(signupService.signup(any()))
                .thenThrow(new DuplicateUserException("Email is already registered"));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Email is already registered"));
    }

    @Test
    void signupReturnsBadRequestForPasswordMismatch() throws Exception {
        when(signupService.signup(any()))
                .thenThrow(new PasswordMismatchException("Password confirmation does not match"));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Password confirmation does not match"));
    }

    private String validRequest() {
        return """
                {
                  "firstName": "Kien",
                  "lastName": "Nguyen Van",
                  "email": "kien@gmail.com",
                  "phoneNumber": "912345678",
                  "password": "password123",
                  "confirmPassword": "password123"
                }
                """;
    }
}
