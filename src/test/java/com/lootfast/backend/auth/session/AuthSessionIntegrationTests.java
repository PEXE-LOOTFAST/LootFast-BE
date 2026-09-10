package com.lootfast.backend.auth.session;

import com.jayway.jsonpath.JsonPath;
import com.lootfast.backend.auth.dto.RefreshTokenResponse;
import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.user.entity.User;
import com.lootfast.backend.user.entity.UserRole;
import com.lootfast.backend.user.entity.UserStatus;
import com.lootfast.backend.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthSessionIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private AuthSessionRepository authSessionRepository;
    @Autowired private AuthSessionService authSessionService;
    @Autowired private PasswordEncoder passwordEncoder;

    private User user;

    @BeforeEach
    void setUp() {
        authSessionRepository.deleteAll();
        userRepository.deleteAll();
        user = userRepository.saveAndFlush(new User(
                "Kien",
                "Nguyen Van",
                "kien@gmail.com",
                "912345678",
                passwordEncoder.encode("password123"),
                UserRole.BUYER,
                UserStatus.VERIFIED
        ));
    }

    @AfterEach
    void cleanUp() {
        authSessionRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void signinRefreshRotationAndLogoutWorkEndToEnd() throws Exception {
        String signinBody = mockMvc.perform(post("/api/v1/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "kien@gmail.com", "password": "password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String originalRefreshToken = JsonPath.read(signinBody, "$.data.refreshToken");
        assertEquals(1, authSessionRepository.count());

        String refreshBody = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshRequest(originalRefreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String rotatedRefreshToken = JsonPath.read(refreshBody, "$.data.refreshToken");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshRequest(originalRefreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshRequest(rotatedRefreshToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshRequest(rotatedRefreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void concurrentRefreshAllowsOnlyOneRequestToRotateSession() throws Exception {
        AuthSessionService.IssuedSession session = authSessionService.createSession(user);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> results = List.of(
                    executor.submit(() -> refreshAfterSignal(session.refreshToken(), ready, start)),
                    executor.submit(() -> refreshAfterSignal(session.refreshToken(), ready, start))
            );

            ready.await();
            start.countDown();

            long successfulRequests = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    successfulRequests++;
                }
            }

            assertEquals(1, successfulRequests);
        }
    }

    @Test
    void logoutRevokesOnlyTheSelectedDeviceSession() {
        AuthSessionService.IssuedSession firstDevice = authSessionService.createSession(user);
        AuthSessionService.IssuedSession secondDevice = authSessionService.createSession(user);

        authSessionService.logout(firstDevice.refreshToken());

        assertThrows(
                ApiException.class,
                () -> authSessionService.refresh(firstDevice.refreshToken())
        );
        assertDoesNotThrow(() -> authSessionService.refresh(secondDevice.refreshToken()));
    }

    private boolean refreshAfterSignal(
            String refreshToken,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        start.await();

        try {
            RefreshTokenResponse ignored = authSessionService.refresh(refreshToken);
            return true;
        } catch (ApiException exception) {
            return false;
        }
    }

    private String refreshRequest(String refreshToken) {
        return "{\"refreshToken\":\"" + refreshToken + "\"}";
    }
}
