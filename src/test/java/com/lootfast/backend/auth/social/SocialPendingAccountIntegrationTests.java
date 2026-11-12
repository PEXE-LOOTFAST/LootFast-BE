package com.lootfast.backend.auth.social;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.lootfast.backend.auth.dto.SocialLoginRequest;
import com.lootfast.backend.auth.service.JwtService;
import com.lootfast.backend.auth.session.AuthSession;
import com.lootfast.backend.auth.session.AuthSessionRepository;
import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.content.service.LegalContentService;
import com.lootfast.backend.user.entity.*;
import com.lootfast.backend.user.repository.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SocialPendingAccountIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired UserProfileRepository profiles;
    @Autowired UserConsentRepository consents;
    @Autowired ExternalAuthIdentityRepository identities;
    @Autowired AuthSessionRepository sessions;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean SocialNonceService nonces;
    @MockitoBean SocialIdentityTokenVerifier verifier;
    @MockitoBean LegalContentService legal;

    User pending;
    String email;
    String subject;

    @BeforeEach
    void pendingSignup() {
        subject = UUID.randomUUID().toString();
        email = "pending-" + subject + "@gmail.com";
        pending = users.saveAndFlush(new User("Pending", "User", email, null,
                encoder.encode("OriginalPassword123!"), UserRole.BUYER, UserStatus.UNVERIFIED));
        profiles.saveAndFlush(new UserProfile(pending, "Pending User"));
        consents.saveAllAndFlush(List.of(
                new UserConsent(pending, ConsentDocument.TERMS_OF_USE, "terms-v1", Instant.now()),
                new UserConsent(pending, ConsentDocument.PRIVACY_POLICY, "privacy-v1", Instant.now())));
        when(verifier.verify(SocialProvider.GOOGLE, "google-token", "nonce"))
                .thenReturn(new SocialIdentityTokenVerifier.VerifiedSocialIdentity(
                        SocialProvider.GOOGLE, subject, email, "Pending", "User", true));
    }

    @Test
    void googleCompletesPendingSignupWithoutDuplicateUserOrConsentAndDiscardsOldCredentials() throws Exception {
        long usersBefore = users.count();
        String previousAccess = jwt.issueAccessToken(pending).value();
        AuthSession previousSession = oldSession();

        String response = login(true, 200);

        User verified = users.findById(pending.getId()).orElseThrow();
        assertEquals(usersBefore, users.count());
        assertEquals(pending.getPublicId(), verified.getPublicId());
        assertEquals(UserStatus.VERIFIED, verified.getStatus());
        assertNull(verified.getPasswordHash());
        assertEquals(1, verified.getAuthenticationVersion());
        assertEquals(2L, jdbc.queryForObject("select count(*) from user_consents where user_id=?",
                Long.class, pending.getId()));
        assertFalse(sessions.findById(previousSession.getId()).orElseThrow().isActiveAt(Instant.now()));
        assertTrue(identities.findAll().stream().anyMatch(i -> i.getUser().getId().equals(pending.getId())));

        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + previousAccess))
                .andExpect(status().isUnauthorized());
        String access = JsonPath.read(response, "$.data.accessToken");
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/signin").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", "OriginalPassword123!"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
        String refresh = JsonPath.read(response, "$.data.refreshToken");
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("refreshToken", refresh))))
                .andExpect(status().isOk());
        login(false, 200);
        assertEquals(usersBefore, users.count());
        assertEquals(1, users.findById(pending.getId()).orElseThrow().getAuthenticationVersion());
    }

    @Test
    void verifiedAccountKeepsItsExistingPassword() throws Exception {
        pending.verify();
        users.saveAndFlush(pending);
        login(false, 200);
        User result = users.findById(pending.getId()).orElseThrow();
        assertTrue(encoder.matches("OriginalPassword123!", result.getPasswordHash()));
        assertEquals(0, result.getAuthenticationVersion());
    }

    @Test
    void lockedPendingAccountCannotBeActivated() throws Exception {
        pending.lockAccount("Locked for test", Instant.now());
        users.saveAndFlush(pending);
        String response = login(true, 403);
        assertEquals("ACCOUNT_LOCKED", JsonPath.read(response, "$.error.code"));
        assertPending();
    }

    @Test
    void thirdPartyGoogleEmailCannotActivatePendingAccount() throws Exception {
        email = email.replace("@gmail.com", "@example.com");
        pending = users.saveAndFlush(new User("Pending", "User", email, null,
                encoder.encode("OriginalPassword123!"), UserRole.BUYER, UserStatus.UNVERIFIED));
        when(verifier.verify(SocialProvider.GOOGLE, "google-token", "nonce"))
                .thenReturn(new SocialIdentityTokenVerifier.VerifiedSocialIdentity(
                        SocialProvider.GOOGLE, subject, email, "Pending", "User", false));
        String response = login(true, 403);
        assertEquals("ACCOUNT_NOT_VERIFIED", JsonPath.read(response, "$.error.code"));
        assertPending();
    }

    @Test
    void pendingAccountRequiresConsentBeforeActivation() throws Exception {
        String response = login(false, 400);
        assertEquals("CONSENT_REQUIRED", JsonPath.read(response, "$.error.code"));
        assertPending();
    }

    @Test
    void invalidTokenCannotActivatePendingAccount() throws Exception {
        when(verifier.verify(SocialProvider.GOOGLE, "google-token", "nonce"))
                .thenThrow(new ApiException(ErrorCode.INVALID_SOCIAL_TOKEN));
        String response = login(true, 401);
        assertEquals("INVALID_SOCIAL_TOKEN", JsonPath.read(response, "$.error.code"));
        assertPending();
    }

    @Test
    void conflictingGoogleLinkRollsBackVerificationPasswordAndSessionRevocation() throws Exception {
        identities.saveAndFlush(new ExternalAuthIdentity(pending, SocialProvider.GOOGLE,
                "previous-" + subject, email, Instant.now()));
        AuthSession session = oldSession();
        String response = login(true, 409);
        assertEquals("SOCIAL_IDENTITY_CONFLICT", JsonPath.read(response, "$.error.code"));
        assertPending();
        assertEquals(0, users.findById(pending.getId()).orElseThrow().getAuthenticationVersion());
        assertTrue(sessions.findById(session.getId()).orElseThrow().isActiveAt(Instant.now()));
    }

    private AuthSession oldSession() {
        String hash = (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "");
        return sessions.saveAndFlush(new AuthSession(UUID.randomUUID(), pending, hash,
                Instant.now().plusSeconds(3600), Instant.now()));
    }

    private void assertPending() {
        User result = users.findById(pending.getId()).orElseThrow();
        assertEquals(UserStatus.UNVERIFIED, result.getStatus());
        assertTrue(encoder.matches("OriginalPassword123!", result.getPasswordHash()));
    }

    private String login(boolean consent, int expectedStatus) throws Exception {
        var request = new SocialLoginRequest(SocialProvider.GOOGLE, "google-token", "nonce", null, null,
                consent, "terms-v1", consent, "privacy-v1");
        return mvc.perform(post("/api/v1/auth/social/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request)))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
    }
}
