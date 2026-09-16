package com.lootfast.backend.merchant;

import com.lootfast.backend.common.storage.ImageStorage;
import com.lootfast.backend.common.storage.PrivateImageStorage;
import com.lootfast.backend.merchant.application.repository.MerchantApplicationRepository;
import com.lootfast.backend.merchant.entity.MerchantProfile;
import com.lootfast.backend.merchant.repository.MerchantOpeningHourRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MerchantProfileIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private MerchantProfileRepository merchantProfileRepository;
    @Autowired private MerchantOpeningHourRepository openingHourRepository;
    @Autowired private MerchantApplicationRepository merchantApplicationRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @MockitoBean private ImageStorage imageStorage;
    @MockitoBean private PrivateImageStorage privateImageStorage;
    @MockitoBean private MerchantImageUploadRateLimiter merchantImageUploadRateLimiter;

    private String merchantToken;
    private String buyerToken;

    @BeforeEach
    void setUp() throws Exception {
        merchantApplicationRepository.deleteAll();
        openingHourRepository.deleteAll();
        merchantProfileRepository.deleteAll();
        userRepository.deleteAll();

        User merchant = userRepository.save(user(
                "Merchant", "Test", "merchant@lootfast.local", "911111111",
                UserRole.MERCHANT
        ));
        merchantProfileRepository.save(new MerchantProfile(merchant));
        userRepository.save(user(
                "Buyer", "Test", "buyer-profile@lootfast.local", "922222222",
                UserRole.BUYER
        ));

        merchantToken = signin("merchant@lootfast.local");
        buyerToken = signin("buyer-profile@lootfast.local");
    }

    @Test
    void merchantCanViewAndUpdateStoreInformation() throws Exception {
        mockMvc.perform(get("/api/v1/merchants/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(merchantToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.storeName").doesNotExist())
                .andExpect(jsonPath("$.data.logoUrl").doesNotExist());

        mockMvc.perform(put("/api/v1/merchants/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(merchantToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "storeName": "  Bếp Nhà An  ",
                                  "description": "  Đồ ăn Việt cuối ngày  ",
                                  "businessCategory": "Vietnamese food",
                                  "contactEmail": "CONTACT@EXAMPLE.COM",
                                  "contactPhone": "+84912345678"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.storeName").value("Bếp Nhà An"))
                .andExpect(jsonPath("$.data.description").value("Đồ ăn Việt cuối ngày"))
                .andExpect(jsonPath("$.data.businessCategory").value("Vietnamese food"))
                .andExpect(jsonPath("$.data.contactEmail").value("contact@example.com"))
                .andExpect(jsonPath("$.data.contactPhone").value("+84912345678"))
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andExpect(jsonPath("$.data.updatedAt").exists());

        mockMvc.perform(get("/api/v1/merchants/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(merchantToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.storeName").value("Bếp Nhà An"));
    }

    @Test
    void profileValidationAndMerchantAuthorizationAreEnforced() throws Exception {
        mockMvc.perform(put("/api/v1/merchants/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(merchantToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "storeName": " ",
                                  "businessCategory": "Bakery",
                                  "contactEmail": "not-an-email",
                                  "contactPhone": "abc"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.fields.storeName").exists())
                .andExpect(jsonPath("$.error.fields.contactEmail").exists())
                .andExpect(jsonPath("$.error.fields.contactPhone").exists());

        mockMvc.perform(get("/api/v1/merchants/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(buyerToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
    }

    @Test
    void merchantCanManageValidatedLocation() throws Exception {
        mockMvc.perform(put("/api/v1/merchants/me/location")
                        .header(HttpHeaders.AUTHORIZATION, bearer(merchantToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "addressLine": "123 Nguyễn Huệ",
                                  "ward": "Bến Nghé",
                                  "district": "Quận 1",
                                  "city": "Hồ Chí Minh",
                                  "latitude": 10.776889,
                                  "longitude": 106.700806
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.addressLine").value("123 Nguyễn Huệ"))
                .andExpect(jsonPath("$.data.latitude").value(10.776889))
                .andExpect(jsonPath("$.data.longitude").value(106.700806));

        mockMvc.perform(get("/api/v1/merchants/me/location")
                        .header(HttpHeaders.AUTHORIZATION, bearer(merchantToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.city").value("Hồ Chí Minh"));

        mockMvc.perform(put("/api/v1/merchants/me/location")
                        .header(HttpHeaders.AUTHORIZATION, bearer(merchantToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "addressLine": "123 Nguyễn Huệ",
                                  "ward": "Bến Nghé",
                                  "district": "Quận 1",
                                  "city": "Hồ Chí Minh",
                                  "latitude": 90.000001,
                                  "longitude": 106.700806
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.fields.latitude").exists());
    }

    @Test
    void merchantCanUseMultipleNonOverlappingOpeningHourSlots() throws Exception {
        mockMvc.perform(put("/api/v1/merchants/me/opening-hours")
                        .header(HttpHeaders.AUTHORIZATION, bearer(merchantToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slots": [
                                    {"dayOfWeek":"TUESDAY","opensAt":"09:00","closesAt":"17:00"},
                                    {"dayOfWeek":"MONDAY","opensAt":"13:00","closesAt":"17:00"},
                                    {"dayOfWeek":"MONDAY","opensAt":"08:00","closesAt":"11:00"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slots.length()").value(3))
                .andExpect(jsonPath("$.data.slots[0].dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$.data.slots[0].opensAt").value("08:00"))
                .andExpect(jsonPath("$.data.slots[1].opensAt").value("13:00"))
                .andExpect(jsonPath("$.data.slots[2].dayOfWeek").value("TUESDAY"));

        mockMvc.perform(get("/api/v1/merchants/me/opening-hours")
                        .header(HttpHeaders.AUTHORIZATION, bearer(merchantToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slots.length()").value(3))
                .andExpect(jsonPath("$.data.slots[0].dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$.data.slots[0].opensAt").value("08:00"))
                .andExpect(jsonPath("$.data.slots[2].dayOfWeek").value("TUESDAY"));
    }

    @Test
    void overlappingOrReversedSlotsAreRejectedWithoutReplacingCurrentSchedule()
            throws Exception {
        replaceOpeningHours("""
                {
                  "slots": [
                    {"dayOfWeek":"MONDAY","opensAt":"08:00","closesAt":"11:00"}
                  ]
                }
                """);

        mockMvc.perform(put("/api/v1/merchants/me/opening-hours")
                        .header(HttpHeaders.AUTHORIZATION, bearer(merchantToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slots": [
                                    {"dayOfWeek":"MONDAY","opensAt":"08:00","closesAt":"12:00"},
                                    {"dayOfWeek":"MONDAY","opensAt":"11:30","closesAt":"14:00"}
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_OPENING_HOURS"));

        mockMvc.perform(put("/api/v1/merchants/me/opening-hours")
                        .header(HttpHeaders.AUTHORIZATION, bearer(merchantToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slots": [
                                    {"dayOfWeek":"TUESDAY","opensAt":"17:00","closesAt":"09:00"}
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_OPENING_HOURS"));

        mockMvc.perform(get("/api/v1/merchants/me/opening-hours")
                        .header(HttpHeaders.AUTHORIZATION, bearer(merchantToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slots.length()").value(1))
                .andExpect(jsonPath("$.data.slots[0].opensAt").value("08:00"));
    }

    private void replaceOpeningHours(String content) throws Exception {
        mockMvc.perform(put("/api/v1/merchants/me/opening-hours")
                        .header(HttpHeaders.AUTHORIZATION, bearer(merchantToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isOk());
    }

    private String signin(String email) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(
                mockMvc.perform(post("/api/v1/auth/signin")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"" + email
                                        + "\",\"password\":\"password123\"}"))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                "$.data.accessToken"
        );
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

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
