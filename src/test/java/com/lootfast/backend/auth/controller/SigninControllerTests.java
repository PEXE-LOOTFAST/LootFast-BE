package com.lootfast.backend.auth.controller;

import com.lootfast.backend.auth.dto.SigninResponse;
import com.lootfast.backend.auth.service.SigninService;
import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.common.exception.GlobalExceptionHandler;
import com.lootfast.backend.user.entity.UserRole;
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

class SigninControllerTests {

    private SigninService signinService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        signinService = mock(SigninService.class);
        mockMvc = standaloneSetup(new SigninController(signinService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void signinReturnsAccessToken() throws Exception {
        SigninResponse response = new SigninResponse(
                "access-token", "refresh-token", "Bearer", 900, 2_592_000,
                "Kien", "Nguyen Van", "kien@gmail.com", UserRole.BUYER
        );
        when(signinService.signin(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(900))
                .andExpect(jsonPath("$.data.refreshExpiresIn").value(2_592_000))
                .andExpect(jsonPath("$.data.email").value("kien@gmail.com"))
                .andExpect(jsonPath("$.data.role").value("BUYER"));
    }

    @Test
    void signinReturnsUnauthorizedForInvalidCredentials() throws Exception {
        when(signinService.signin(any()))
                .thenThrow(new ApiException(ErrorCode.INVALID_CREDENTIALS,
                        "Invalid email or password"));

        mockMvc.perform(post("/api/v1/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.error.message").value("Invalid email or password"));
    }

    @Test
    void signinReturnsBadRequestForInvalidFields() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "not-an-email", "password": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.fields.email").exists())
                .andExpect(jsonPath("$.error.fields.password").exists());
    }

    private String validRequest() {
        return """
                {"email": "kien@gmail.com", "password": "password123"}
                """;
    }
}
