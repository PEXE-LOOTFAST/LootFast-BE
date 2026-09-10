package com.lootfast.backend.auth.controller;

import com.lootfast.backend.auth.dto.RefreshTokenResponse;
import com.lootfast.backend.auth.session.AuthSessionService;
import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class AuthSessionControllerTests {

    private AuthSessionService authSessionService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authSessionService = mock(AuthSessionService.class);
        mockMvc = standaloneSetup(new AuthSessionController(authSessionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void refreshReturnsRotatedTokenPair() throws Exception {
        when(authSessionService.refresh("refresh-token")).thenReturn(new RefreshTokenResponse(
                "access-token",
                "rotated-refresh-token",
                "Bearer",
                900,
                2_591_000
        ));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "refresh-token"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("rotated-refresh-token"))
                .andExpect(jsonPath("$.data.expiresIn").value(900))
                .andExpect(jsonPath("$.data.refreshExpiresIn").value(2_591_000));
    }

    @Test
    void refreshReturnsUnauthorizedForInvalidToken() throws Exception {
        when(authSessionService.refresh("invalid-token"))
                .thenThrow(new ApiException(
                        ErrorCode.INVALID_REFRESH_TOKEN,
                        "Invalid or expired refresh token"
                ));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "invalid-token"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"))
                .andExpect(jsonPath("$.error.message")
                        .value("Invalid or expired refresh token"));
    }

    @Test
    void refreshValidatesMissingToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.fields.refreshToken").exists());
    }

    @Test
    void refreshReturnsBadRequestWhenBodyIsMissing() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.message")
                        .value("Request body is missing or malformed"));
    }

    @Test
    void refreshReturnsBadRequestForMalformedJson() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.message")
                        .value("Request body is missing or malformed"));
    }

    @Test
    void refreshReturnsUnsupportedMediaTypeForPlainText() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("refresh-token"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void logoutRevokesSessionAndReturnsNoContent() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "refresh-token"}
                                """))
                .andExpect(status().isNoContent());

        verify(authSessionService).logout("refresh-token");
    }

    @Test
    void logoutValidatesMissingToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.refreshToken").exists());
    }

    @Test
    void logoutReturnsBadRequestForMalformedJson() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.message")
                        .value("Request body is missing or malformed"));
    }
}
