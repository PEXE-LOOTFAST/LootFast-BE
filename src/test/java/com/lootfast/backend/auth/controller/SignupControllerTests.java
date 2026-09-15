package com.lootfast.backend.auth.controller;

import com.lootfast.backend.auth.dto.SignupResponse;
import com.lootfast.backend.auth.dto.OnboardingAction;
import com.lootfast.backend.auth.service.SignupService;
import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.common.exception.GlobalExceptionHandler;
import com.lootfast.backend.user.entity.AccountType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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
    void signupReturnsOtpSentMessage() throws Exception {
        when(signupService.signup(any())).thenReturn(
                new SignupResponse(
                        "Verification code sent to kien@gmail.com",
                        "kien@gmail.com",
                        AccountType.BUYER,
                        OnboardingAction.BUYER_HOME
                ));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("kien@gmail.com"))
                .andExpect(jsonPath("$.data.message").exists())
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void signupReturnsBadRequestForInvalidFields() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Kien",
                                  "lastName": "Nguyen Van",
                                  "email": "kien@yahoo.com",
                                  "phoneNumber": "123",
                                  "password": "password123",
                                  "confirmPassword": "password123",
                                  "accountType": "BUYER",
                                  "termsAccepted": true,
                                  "termsVersion": "2026-09",
                                  "privacyPolicyAccepted": true,
                                  "privacyPolicyVersion": "2026-09"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.fields.email").exists())
                .andExpect(jsonPath("$.error.fields.phoneNumber").exists());
    }

    @Test
    void signupReturnsConflictForDuplicateEmail() throws Exception {
        when(signupService.signup(any()))
                .thenThrow(new ApiException(ErrorCode.DUPLICATE_EMAIL,
                        "Email is already registered"));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_EMAIL"));
    }

    @Test
    void signupReturnsBadRequestForPasswordMismatch() throws Exception {
        when(signupService.signup(any()))
                .thenThrow(new ApiException(ErrorCode.PASSWORD_MISMATCH,
                        "Password confirmation does not match"));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PASSWORD_MISMATCH"));
    }

    private String validRequest() {
        return """
                {
                  "firstName": "Kien",
                  "lastName": "Nguyen Van",
                  "email": "kien@gmail.com",
                  "phoneNumber": "912345678",
                  "password": "password123",
                  "confirmPassword": "password123",
                  "accountType": "BUYER",
                  "termsAccepted": true,
                  "termsVersion": "2026-09",
                  "privacyPolicyAccepted": true,
                  "privacyPolicyVersion": "2026-09"
                }
                """;
    }
}
