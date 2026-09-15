package com.lootfast.backend.auth.social;

import com.lootfast.backend.auth.dto.SocialLoginRequest;
import com.lootfast.backend.auth.service.ConsentService;
import com.lootfast.backend.user.entity.User;
import com.lootfast.backend.user.entity.UserProfile;
import com.lootfast.backend.user.entity.UserRole;
import com.lootfast.backend.user.entity.UserStatus;
import com.lootfast.backend.user.repository.UserProfileRepository;
import com.lootfast.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SocialLoginTransactionServiceTests {

    private static final Instant NOW = Instant.parse("2026-09-15T04:00:00Z");

    @Mock private ExternalAuthIdentityRepository identityRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private ConsentService consentService;

    private SocialLoginTransactionService service;

    @BeforeEach
    void setUp() {
        service = new SocialLoginTransactionService(
                identityRepository,
                userRepository,
                userProfileRepository,
                consentService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void firstSocialLoginAlwaysCreatesVerifiedBuyerRole() {
        SocialIdentityTokenVerifier.VerifiedSocialIdentity identity =
                new SocialIdentityTokenVerifier.VerifiedSocialIdentity(
                        SocialProvider.GOOGLE,
                        "google-subject",
                        "user@gmail.com",
                        "An",
                        "Nguyen"
                );
        when(identityRepository.findByProviderAndProviderSubject(
                SocialProvider.GOOGLE,
                "google-subject"
        )).thenReturn(Optional.empty());
        when(userRepository.findByEmailForUpdate("user@gmail.com"))
                .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));

        User user = service.findOrCreate(identity, request(true));

        assertEquals(UserStatus.VERIFIED, user.getStatus());
        assertEquals(UserRole.BUYER, user.getRole());
        assertNull(user.getPasswordHash());
        assertNull(user.getPhoneNumber());
        verify(consentService).recordRequiredConsents(user, "terms-v1", "privacy-v1");
        verify(userProfileRepository).save(any(UserProfile.class));
        verify(identityRepository).saveAndFlush(any(ExternalAuthIdentity.class));
    }

    @Test
    void existingProviderIdentityDoesNotRequireRegistrationFieldsAgain() {
        User user = new User(
                "An", "Nguyen", "user@gmail.com", null, null,
                UserRole.BUYER, UserStatus.VERIFIED
        );
        ExternalAuthIdentity existing = new ExternalAuthIdentity(
                user,
                SocialProvider.APPLE,
                "apple-subject",
                "old@example.com",
                NOW.minusSeconds(60)
        );
        when(identityRepository.findByProviderAndProviderSubject(
                SocialProvider.APPLE,
                "apple-subject"
        )).thenReturn(Optional.of(existing));
        SocialIdentityTokenVerifier.VerifiedSocialIdentity identity =
                new SocialIdentityTokenVerifier.VerifiedSocialIdentity(
                        SocialProvider.APPLE,
                        "apple-subject",
                        "relay@privaterelay.appleid.com",
                        "",
                        ""
                );

        User result = service.findOrCreate(identity, request(false));

        assertEquals(user, result);
        verify(userRepository, never()).save(any());
        verify(consentService, never()).recordRequiredConsents(any(), any(), any());
    }

    private SocialLoginRequest request(boolean includeRegistrationFields) {
        return new SocialLoginRequest(
                SocialProvider.GOOGLE,
                "identity-token",
                "nonce",
                null,
                null,
                includeRegistrationFields ? true : null,
                includeRegistrationFields ? "terms-v1" : null,
                includeRegistrationFields ? true : null,
                includeRegistrationFields ? "privacy-v1" : null
        );
    }
}
