package com.lootfast.backend.merchant.application;

import com.jayway.jsonpath.JsonPath;
import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.common.storage.ImageStorage;
import com.lootfast.backend.common.storage.PrivateImageStorage;
import com.lootfast.backend.common.storage.StoredImage;
import com.lootfast.backend.common.storage.StoredPrivateImage;
import com.lootfast.backend.merchant.application.repository.MerchantApplicationRepository;
import com.lootfast.backend.merchant.application.service.MerchantApplicationSubmissionService;
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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MerchantApplicationIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private MerchantApplicationRepository applicationRepository;
    @Autowired private MerchantProfileRepository merchantProfileRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private MerchantApplicationSubmissionService submissionService;

    @MockitoBean private PrivateImageStorage privateImageStorage;
    @MockitoBean private ImageStorage imageStorage;
    @MockitoBean private MerchantImageUploadRateLimiter merchantImageUploadRateLimiter;

    private User buyer;
    private User admin;

    @BeforeEach
    void setUp() {
        applicationRepository.deleteAll();
        merchantProfileRepository.deleteAll();
        userRepository.deleteAll();

        buyer = userRepository.save(user(
                "Buyer", "Test", "buyer@lootfast.local", "911111111", UserRole.BUYER
        ));
        admin = userRepository.save(user(
                "Admin", "Test", "admin@lootfast.local", "922222222", UserRole.ADMIN
        ));

        when(privateImageStorage.uploadPrivate(anyString(), any()))
                .thenReturn(new StoredPrivateImage("private-license-key", "png"));
    }

    @Test
    void approvalAddsMerchantWithoutRemovingBuyerAndCreatesProfile() throws Exception {
        String buyerToken = signin("buyer@lootfast.local");
        String adminToken = signin("admin@lootfast.local");
        String applicationId = submit(buyerToken);

        mockMvc.perform(get("/api/v1/admin/merchant-applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/merchant-applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("PENDING"));

        when(privateImageStorage.createDownloadUrl(
                anyString(),
                anyString(),
                any()
        )).thenReturn("https://api.cloudinary.test/private-license");
        mockMvc.perform(get("/api/v1/admin/merchant-applications/"
                        + applicationId + "/license-download-url")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url")
                        .value("https://api.cloudinary.test/private-license"))
                .andExpect(jsonPath("$.data.expiresAt").exists());

        mockMvc.perform(post("/api/v1/admin/merchant-applications/"
                        + applicationId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.userRoles",
                        containsInAnyOrder("BUYER", "MERCHANT")));

        User approved = userRepository.findByIdWithRoles(buyer.getId()).orElseThrow();
        assertTrue(approved.hasRole(UserRole.BUYER));
        assertTrue(approved.hasRole(UserRole.MERCHANT));
        assertTrue(merchantProfileRepository.existsByUserId(buyer.getId()));

        String refreshedBuyerToken = signin("buyer@lootfast.local");
        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + refreshedBuyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("MERCHANT"))
                .andExpect(jsonPath("$.data.roles",
                        containsInAnyOrder("BUYER", "MERCHANT")));

        when(imageStorage.upload(anyString(), any(), any())).thenReturn(
                new StoredImage("merchant-logo-key", "https://example.test/logo.png")
        );
        mockMvc.perform(multipart("/api/v1/merchants/me/logo")
                        .file(imagePart())
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + refreshedBuyerToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/merchant-applications/"
                        + applicationId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());
        assertEquals(1, merchantProfileRepository.count());
    }

    @Test
    void rejectedApplicationCanBeSubmittedAgain() throws Exception {
        String buyerToken = signin("buyer@lootfast.local");
        String adminToken = signin("admin@lootfast.local");
        String applicationId = submit(buyerToken);

        mockMvc.perform(post("/api/v1/admin/merchant-applications/"
                        + applicationId + "/reject")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"License image is unreadable\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectionReason")
                        .value("License image is unreadable"));

        submit(buyerToken);
        verify(privateImageStorage).deletePrivate("private-license-key");
        mockMvc.perform(get("/api/v1/merchant-applications/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.userRoles[0]").value("BUYER"));
    }

    @Test
    void simultaneousSubmissionsAllowOnlyOnePendingApplication() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        Callable<Object> submit = () -> {
            start.await();
            try {
                return submissionService.submit(
                        buyer.getId().toString(),
                        "0312345678",
                        imagePart()
                );
            } catch (ApiException exception) {
                return exception;
            }
        };

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Object>> futures = List.of(
                    executor.submit(submit),
                    executor.submit(submit)
            );
            start.countDown();

            List<Object> results = futures.stream().map(future -> {
                try {
                    return future.get();
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            }).toList();

            long successes = results.stream()
                    .filter(result -> !(result instanceof ApiException))
                    .count();
            List<ApiException> failures = results.stream()
                    .filter(ApiException.class::isInstance)
                    .map(ApiException.class::cast)
                    .toList();

            assertEquals(1, successes);
            assertEquals(1, failures.size());
            assertEquals(
                    ErrorCode.MERCHANT_APPLICATION_ALREADY_PENDING,
                    failures.getFirst().getErrorCode()
            );
            assertEquals(1, applicationRepository.count());
        }
    }

    private String submit(String token) throws Exception {
        String body = mockMvc.perform(multipart("/api/v1/merchant-applications")
                        .file(new MockMultipartFile(
                                "businessLicenseNumber",
                                "",
                                MediaType.TEXT_PLAIN_VALUE,
                                "0312345678".getBytes()
                        ))
                        .file(imagePart())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.applicationId");
    }

    private String signin(String email) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email
                                + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.accessToken");
    }

    private User user(
            String firstName,
            String lastName,
            String email,
            String phone,
            UserRole role
    ) {
        return new User(
                firstName,
                lastName,
                email,
                phone,
                passwordEncoder.encode("password123"),
                role,
                UserStatus.VERIFIED
        );
    }

    private MockMultipartFile imagePart() throws IOException {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return new MockMultipartFile(
                "file",
                "license.png",
                MediaType.IMAGE_PNG_VALUE,
                output.toByteArray()
        );
    }
}
