package com.lootfast.backend.security;

import com.jayway.jsonpath.JsonPath;
import com.lootfast.backend.auth.service.OtpService;
import com.lootfast.backend.common.storage.ImageStorage;
import com.lootfast.backend.common.storage.StoredImage;
import com.lootfast.backend.merchant.entity.MerchantProfile;
import com.lootfast.backend.merchant.repository.MerchantProfileRepository;
import com.lootfast.backend.merchant.service.MerchantImageUploadRateLimiter;
import com.lootfast.backend.user.entity.User;
import com.lootfast.backend.user.entity.UserRole;
import com.lootfast.backend.user.entity.UserStatus;
import com.lootfast.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
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
class AuthorizationIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MerchantProfileRepository merchantProfileRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // Mock OtpService để signup không gọi Redis thật trong integration test.
    // Test này chỉ kiểm tra authorization flow, không test OTP.
    @MockitoBean
    private OtpService otpService;

    @MockitoBean
    private ImageStorage imageStorage;

    @MockitoBean
    private MerchantImageUploadRateLimiter merchantImageUploadRateLimiter;

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
                .andExpect(jsonPath("$.data.roles[0]").value("BUYER"))
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
    void corsPreflightAllowsConfiguredFrontendOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/users/me")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name())
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                HttpHeaders.AUTHORIZATION + ",Idempotency-Key"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "http://localhost:5173"
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        org.hamcrest.Matchers.containsString("Idempotency-Key")
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                        org.hamcrest.Matchers.containsString(HttpMethod.GET.name())
                ));
    }

    @Test
    void corsPreflightRejectsUnconfiguredOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/users/me")
                        .header(HttpHeaders.ORIGIN, "https://untrusted.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name()))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void discoveryReadsArePublicButFavoritesRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/deals/{dealId}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("DEAL_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/stores/{merchantId}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MERCHANT_PROFILE_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/users/me/favorites"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void merchantDealManagementRequiresMerchantAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/merchants/me/deals"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        String buyerToken = signupAndSignin();
        mockMvc.perform(get("/api/v1/merchants/me/deals")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
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
        when(imageStorage.upload(any(), any(), any())).thenReturn(new StoredImage(
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
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "logo.png",
                MediaType.IMAGE_PNG_VALUE,
                pngImage()
        );

        MvcResult result = mockMvc.perform(multipart("/api/v1/merchants/me/logo")
                        .file(file)
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.error.message").value("Access is denied"))
                .andExpect(header().exists("X-Trace-Id"))
                .andReturn();

        assertResponseTraceMatchesHeader(result);
    }

    @Test
    void merchantCanUploadLogoToOwnProfile() throws Exception {
        User merchant = userRepository.save(new User(
                "Kien",
                "Nguyen Van",
                "kien@gmail.com",
                "912345678",
                passwordEncoder.encode("password123"),
                UserRole.MERCHANT,
                UserStatus.VERIFIED
        ));
        merchantProfileRepository.save(new MerchantProfile(merchant));
        String token = signin();
        String logoUrl = "https://res.cloudinary.com/demo/merchant-logo.png";
        when(imageStorage.upload(any(), any(), any())).thenReturn(new StoredImage(
                "lootfast/merchant-profiles/test-user/logo",
                logoUrl
        ));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "logo.png",
                MediaType.IMAGE_PNG_VALUE,
                pngImage()
        );

        mockMvc.perform(multipart("/api/v1/merchants/me/logo")
                        .file(file)
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.imageType").value("LOGO"))
                .andExpect(jsonPath("$.data.url").value(logoUrl));

        assertEquals(
                logoUrl,
                merchantProfileRepository.findByUserId(merchant.getId())
                        .orElseThrow()
                        .getLogoUrl()
        );
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

        return signin();
    }

    private String signin() throws Exception {
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
                  "confirmPassword": "password123",
                  "termsAccepted": true,
                  "termsVersion": "2026-09",
                  "privacyPolicyAccepted": true,
                  "privacyPolicyVersion": "2026-09"
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
}
