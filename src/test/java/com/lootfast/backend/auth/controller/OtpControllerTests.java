package com.lootfast.backend.auth.controller;

import com.lootfast.backend.auth.service.OtpService;
import com.lootfast.backend.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class OtpControllerTests {

    private OtpService otpService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        otpService = mock(OtpService.class);
        mockMvc = standaloneSetup(new OtpController(otpService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void verifyOtpUsesCanonicalApiRoute() throws Exception {
        mockMvc.perform(post("/api/v1/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "user@gmail.com", "otp": "123456"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.message").value(
                        "Email verified. You can now sign in."));

        verify(otpService).verifyOtp("user@gmail.com", "123456");
    }

    @Test
    void resendOtpAlwaysReturnsGenericSuccess() throws Exception {
        mockMvc.perform(post("/api/v1/auth/resend-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "unknown@gmail.com"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.message").value(
                        "If the email exists and is unverified, a new OTP has been sent."));

        verify(otpService).resendOtp("unknown@gmail.com");
    }

    @Test
    void verifyOtpRejectsNonNumericCode() throws Exception {
        mockMvc.perform(post("/api/v1/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "user@gmail.com", "otp": "ABCDEF"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.fields.otp").value(
                        "OTP must contain exactly 6 digits"));

        verify(otpService, never()).verifyOtp("user@gmail.com", "ABCDEF");
    }
}
