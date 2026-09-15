package com.lootfast.backend.auth.service;

import com.lootfast.backend.auth.dto.SignupRequest;
import com.lootfast.backend.auth.dto.SignupResponse;
import com.lootfast.backend.auth.event.SignupCreatedEvent;
import com.lootfast.backend.auth.dto.OnboardingAction;
import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.user.entity.User;
import com.lootfast.backend.user.entity.AccountType;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SignupServiceTests {

    @Mock private UserRepository userRepository;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private ConsentService consentService;
    @Mock private OnboardingService onboardingService;

    private SignupService signupService;

    @BeforeEach
    void setUp() {
        signupService = new SignupService(
                userRepository,
                userProfileRepository,
                passwordEncoder,
                eventPublisher,
                consentService,
                onboardingService
        );
    }

    @Test
    void signupCreatesUnverifiedBuyerAndPublishesOtpEvent() {
        SignupRequest request = request("New.User@Gmail.com", "912345678", "password123");
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(onboardingService.nextAction(any(User.class)))
                .thenReturn(OnboardingAction.BUYER_HOME);

        SignupResponse response = signupService.signup(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertEquals("new.user@gmail.com", saved.getEmail());
        assertEquals("encoded-password", saved.getPasswordHash());
        assertEquals(UserRole.BUYER, saved.getRole());
        assertEquals(AccountType.BUYER, saved.getAccountType());
        assertEquals(UserStatus.UNVERIFIED, saved.getStatus());

        ArgumentCaptor<UserProfile> profileCaptor = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileRepository).save(profileCaptor.capture());
        assertEquals("An Nguyen Van", profileCaptor.getValue().getDisplayName());

        verify(eventPublisher).publishEvent(new SignupCreatedEvent("new.user@gmail.com"));
        verify(consentService).recordRequiredConsents(saved, "2026-09", "2026-09");

        assertEquals("new.user@gmail.com", response.email());
        assertTrue(response.message().contains("new.user@gmail.com"));
    }

    @Test
    void merchantSelectionRoutesToApplicationWithoutGrantingMerchantRole() {
        SignupRequest request = new SignupRequest(
                "An", "Nguyen", "merchant@gmail.com", "912345678",
                "password123", "password123", AccountType.MERCHANT,
                true, "2026-09", true, "2026-09"
        );
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));
        when(onboardingService.nextAction(any(User.class)))
                .thenReturn(OnboardingAction.MERCHANT_APPLICATION);

        SignupResponse response = signupService.signup(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals(AccountType.MERCHANT, captor.getValue().getAccountType());
        assertEquals(UserRole.BUYER, captor.getValue().getRole());
        assertEquals(OnboardingAction.MERCHANT_APPLICATION, response.nextAction());
    }

    @Test
    void signupRejectsMismatchedPasswordConfirmation() {
        SignupRequest request = new SignupRequest(
                "An", "Nguyen", "an@gmail.com", "912345678", "password123", "different123",
                AccountType.BUYER, true, "2026-09", true, "2026-09"
        );

        ApiException ex = assertThrows(ApiException.class, () -> signupService.signup(request));
        assertEquals(ErrorCode.PASSWORD_MISMATCH, ex.getErrorCode());
        verifyNoInteractions(
                userRepository,
                userProfileRepository,
                passwordEncoder,
                eventPublisher,
                consentService,
                onboardingService
        );
    }

    @Test
    void signupRejectsExistingEmail() {
        SignupRequest request = request("existing@gmail.com", "912345678", "password123");
        when(userRepository.existsByEmailIgnoreCase("existing@gmail.com")).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class, () -> signupService.signup(request));
        assertEquals(ErrorCode.DUPLICATE_EMAIL, ex.getErrorCode());
        verify(userRepository, never()).save(any());
        verifyNoInteractions(userProfileRepository);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void signupRejectsExistingPhoneNumber() {
        SignupRequest request = request("new@gmail.com", "912345678", "password123");
        when(userRepository.existsByPhoneNumber("912345678")).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class, () -> signupService.signup(request));
        assertEquals(ErrorCode.DUPLICATE_PHONE, ex.getErrorCode());
        verify(userRepository, never()).save(any());
        verifyNoInteractions(userProfileRepository);
        verifyNoInteractions(eventPublisher);
    }

    private SignupRequest request(String email, String phone, String password) {
        return new SignupRequest(
                "An", "Nguyen Van", email, phone, password, password,
                AccountType.BUYER, true, "2026-09", true, "2026-09"
        );
    }
}
