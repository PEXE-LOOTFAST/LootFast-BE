package com.lootfast.backend.auth.controller;

import com.lootfast.backend.auth.dto.SigninResponse;
import com.lootfast.backend.auth.exception.InvalidCredentialsException;
import com.lootfast.backend.auth.service.SigninService;
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
                "access-token",
                "Bearer",
                900,
                1L,
                "Kien",
                "Nguyen Van",
                "kien@gmail.com",
                UserRole.BUYER
        );
        when(signinService.signin(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.email").value("kien@gmail.com"))
                .andExpect(jsonPath("$.role").value("BUYER"));
    }

    @Test
    void signinReturnsUnauthorizedForInvalidCredentials() throws Exception {
        when(signinService.signin(any()))
                .thenThrow(new InvalidCredentialsException("Invalid email or password"));

        mockMvc.perform(post("/api/v1/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void signinReturnsBadRequestForInvalidFields() throws Exception {
        String invalidRequest = """
                {
                  "email": "not-an-email",
                  "password": ""
                }
                """;

        mockMvc.perform(post("/api/v1/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.validationErrors.email").exists())
                .andExpect(jsonPath("$.validationErrors.password").exists());
    }

    private String validRequest() {
        return """
                {
                  "email": "kien@gmail.com",
                  "password": "password123"
                }
                """;
    }
}
