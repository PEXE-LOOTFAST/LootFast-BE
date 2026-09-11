package com.lootfast.backend.security;

import com.jayway.jsonpath.JsonPath;
import com.lootfast.backend.auth.service.OtpService;
import com.lootfast.backend.common.storage.AvatarStorage;
import com.lootfast.backend.common.storage.StoredAvatar;
import com.lootfast.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@SpringBootTest
@AutoConfigureMockMvc
@Import(AuthorizationIntegrationTests.MerchantOnlyTestController.class)
class AuthorizationIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    // Mock OtpService để signup không gọi Redis thật trong integration test.
    // Test này chỉ kiểm tra authorization flow, không test OTP.
    @MockitoBean
    private OtpService otpService;

    @MockitoBean
    private AvatarStorage avatarStorage;

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
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.firstName").value("Kien"))
                .andExpect(jsonPath("$.data.lastName").value("Nguyen Van"))
                .andExpect(jsonPath("$.data.email").value("kien@gmail.com"))
                .andExpect(jsonPath("$.data.phoneNumber").value("912345678"))
                .andExpect(jsonPath("$.data.role").value("BUYER"))
                .andExpect(jsonPath("$.data.displayName").value("Kien Nguyen Van"))
                .andExpect(jsonPath("$.data.avatarUrl").isEmpty())
                .andExpect(jsonPath("$.data.pickupArea").isEmpty())
                .andExpect(jsonPath("$.data.favoriteCategories").isArray())
                .andExpect(jsonPath("$.data.favoriteCategories").isEmpty())
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void currentUserReturnsUnauthorizedWithoutToken() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.error.message").value("Authentication is required"))
                .andExpect(header().exists("X-Trace-Id"))
                .andReturn();

        assertResponseTraceMatchesHeader(result);
    }

    @Test
    void currentUserReturnsUnauthorizedForInvalidToken() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void currentUserReturnsUnauthorizedWhenUserWasDeleted() throws Exception {
        String token = signupAndSignin();
        userRepository.deleteAll();

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message")
                        .value("Authenticated user is no longer available"));
    }

    @Test
    void authenticatedUserCanUploadAvatarAndReadNewUrlFromProfile() throws Exception {
        String token = signupAndSignin();
        String avatarUrl = "https://res.cloudinary.com/demo/avatar.png";
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.png",
                MediaType.IMAGE_PNG_VALUE,
                pngImage()
        );
        when(avatarStorage.upload(any(), any())).thenReturn(new StoredAvatar(
                "lootfast/user-avatars/test-user",
                avatarUrl
        ));

        mockMvc.perform(multipart("/api/v1/users/me/avatar")
                        .file(file)
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.avatarUrl").value(avatarUrl));

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatarUrl").value(avatarUrl));
    }

    @Test
    void avatarUploadRejectsMissingFilePart() throws Exception {
        String token = signupAndSignin();

        mockMvc.perform(multipart("/api/v1/users/me/avatar")
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_AVATAR"))
                .andExpect(jsonPath("$.error.message").value("Avatar file is required"));
    }

    @Test
    void buyerReceivesForbiddenFromMerchantOnlyEndpoint() throws Exception {
        String token = signupAndSignin();

        MvcResult result = mockMvc.perform(get("/test/merchant-only")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.error.message").value("Access is denied"))
                .andExpect(header().exists("X-Trace-Id"))
                .andReturn();

        assertResponseTraceMatchesHeader(result);
    }

    private String signupAndSignin() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupRequest()))
                .andExpect(status().isCreated());
        verify(otpService).sendSignupOtp("kien@gmail.com");

        // Bypass OTP trong integration test: set VERIFIED thẳng qua repository
        userRepository.findByEmailIgnoreCase("kien@gmail.com").ifPresent(user -> {
            user.verify();
            userRepository.save(user);
        });

        String body = mockMvc.perform(post("/api/v1/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signinRequest()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return JsonPath.read(body, "$.data.accessToken");
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
                {"email": "kien@gmail.com", "password": "password123"}
                """;
    }

    private byte[] pngImage() throws IOException {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private void assertResponseTraceMatchesHeader(MvcResult result) throws Exception {
        String headerTraceId = result.getResponse().getHeader("X-Trace-Id");
        String bodyTraceId = JsonPath.read(result.getResponse().getContentAsString(), "$.traceId");
        assertNotEquals("no-trace", bodyTraceId);
        assertEquals(headerTraceId, bodyTraceId);
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
