package com.lootfast.backend.auth.password;

import com.lootfast.backend.auth.dto.PasswordResetConfirmRequest;
import com.lootfast.backend.auth.otp.OtpCheckResult;
import com.lootfast.backend.auth.otp.ResendPermit;
import com.lootfast.backend.auth.session.AuthSessionRepository;
import com.lootfast.backend.common.email.EmailSender;
import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.user.entity.User;
import com.lootfast.backend.user.entity.UserRole;
import com.lootfast.backend.user.entity.UserStatus;
import com.lootfast.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTests {

    private static final String EMAIL = "user@gmail.com";
    private static final Instant NOW = Instant.parse("2026-09-15T04:00:00Z");
    private static final String SECRET =
            "test-otp-hmac-secret-that-is-at-least-32-characters";

    @Mock private PasswordResetOtpStore otpStore;
    @Mock private UserRepository userRepository;
    @Mock private AuthSessionRepository authSessionRepository;
    @Mock private EmailSender emailSender;
    @Mock private PasswordEncoder passwordEncoder;

    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(
                otpStore,
                userRepository,
                authSessionRepository,
                emailSender,
                passwordEncoder,
                Clock.fixed(NOW, ZoneOffset.UTC),
                SECRET
        );
    }

    @Test
    void requestResetSendsOtpOnlyForVerifiedAccount() {
        when(otpStore.reserveRequest(EMAIL, 3)).thenReturn(ResendPermit.ALLOWED);
        when(userRepository.findByEmailIgnoreCase(EMAIL))
                .thenReturn(Optional.of(user()));

        service.requestReset(" User@Gmail.com ");

        verify(otpStore).saveOtp(eq(EMAIL), anyString());
        verify(emailSender).sendPasswordResetOtp(eq(EMAIL), anyString());
    }

    @Test
    void requestResetDoesNotRevealUnknownAccount() {
        when(otpStore.reserveRequest(EMAIL, 3)).thenReturn(ResendPermit.ALLOWED);
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty());

        service.requestReset(EMAIL);

        verify(otpStore, never()).saveOtp(eq(EMAIL), anyString());
        verifyNoInteractions(emailSender);
    }

    @Test
    void validOtpChangesPasswordAndRevokesRefreshSessions() {
        User user = user();
        when(userRepository.findByEmailForUpdate(EMAIL)).thenReturn(Optional.of(user));
        when(otpStore.consumeOtp(eq(EMAIL), anyString(), eq(5)))
                .thenReturn(OtpCheckResult.match());
        when(passwordEncoder.encode("new-password")).thenReturn("new-password-hash");

        service.confirmReset(request("123456", "new-password", "new-password"));

        assertEquals("new-password-hash", user.getPasswordHash());
        verify(authSessionRepository).revokeActiveSessions(user.getId(), NOW);
    }

    @Test
    void invalidOtpLeavesPasswordAndSessionsUnchanged() {
        User user = user();
        when(userRepository.findByEmailForUpdate(EMAIL)).thenReturn(Optional.of(user));
        when(otpStore.consumeOtp(eq(EMAIL), anyString(), eq(5)))
                .thenReturn(OtpCheckResult.invalid(2));

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.confirmReset(request("000000", "new-password", "new-password"))
        );

        assertEquals(ErrorCode.OTP_INVALID, exception.getErrorCode());
        assertEquals("old-password-hash", user.getPasswordHash());
        verifyNoInteractions(passwordEncoder, authSessionRepository);
    }

    @Test
    void mismatchedPasswordDoesNotConsumeOtp() {
        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.confirmReset(request("123456", "new-password", "different"))
        );

        assertEquals(ErrorCode.PASSWORD_MISMATCH, exception.getErrorCode());
        verifyNoInteractions(otpStore, userRepository, passwordEncoder, authSessionRepository);
    }

    private PasswordResetConfirmRequest request(
            String otp,
            String password,
            String confirmation
    ) {
        return new PasswordResetConfirmRequest(EMAIL, otp, password, confirmation);
    }

    private User user() {
        return new User(
                "An",
                "Nguyen",
                EMAIL,
                "912345678",
                "old-password-hash",
                UserRole.BUYER,
                UserStatus.VERIFIED
        );
    }
}
