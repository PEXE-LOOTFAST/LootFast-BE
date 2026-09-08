package com.lootfast.backend.security;

import com.jayway.jsonpath.JsonPath;
import com.lootfast.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(AuthorizationIntegrationTests.MerchantOnlyTestController.class)
class AuthorizationIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void currentUserReturnsProfileForValidBearerToken() throws Exception {
        String token = signupAndSignin();

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Kien"))
                .andExpect(jsonPath("$.lastName").value("Nguyen Van"))
                .andExpect(jsonPath("$.email").value("kien@gmail.com"))
                .andExpect(jsonPath("$.phoneNumber").value("912345678"))
                .andExpect(jsonPath("$.role").value("BUYER"))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void currentUserReturnsJsonUnauthorizedWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Authentication is required"))
                .andExpect(jsonPath("$.path").value("/api/v1/users/me"));
    }

    @Test
    void currentUserReturnsJsonUnauthorizedForInvalidToken() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Authentication is required"))
                .andExpect(jsonPath("$.path").value("/api/v1/users/me"));
    }

    @Test
    void currentUserReturnsUnauthorizedWhenTokenUserWasDeleted() throws Exception {
        String token = signupAndSignin();
        userRepository.deleteAll();

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message")
                        .value("Authenticated user is no longer available"));
    }

    @Test
    void buyerReceivesJsonForbiddenFromMerchantOnlyEndpoint() throws Exception {
        String token = signupAndSignin();

        mockMvc.perform(get("/test/merchant-only")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Access is denied"))
                .andExpect(jsonPath("$.path").value("/test/merchant-only"));
    }

    private String signupAndSignin() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupRequest()))
                .andExpect(status().isCreated());

        String responseBody = mockMvc.perform(post("/api/v1/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signinRequest()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return JsonPath.read(responseBody, "$.accessToken");
    }

    private String signupRequest() {
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

    private String signinRequest() {
        return """
                {
                  "email": "kien@gmail.com",
                  "password": "password123"
                }
                """;
    }

    @RestController
    static class MerchantOnlyTestController {

        @GetMapping("/test/merchant-only")
        @PreAuthorize("hasRole('MERCHANT')")
        String merchantOnly() {
            return "ok";
        }
    }
}
