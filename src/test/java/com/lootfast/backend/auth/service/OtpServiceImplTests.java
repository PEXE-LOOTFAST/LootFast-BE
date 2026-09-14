package com.lootfast.backend.auth.service;

import com.lootfast.backend.auth.otp.OtpCheckResult;
import com.lootfast.backend.auth.otp.OtpStore;
import com.lootfast.backend.auth.otp.ResendPermit;
import com.lootfast.backend.common.email.EmailSender;
import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.user.entity.User;
import com.lootfast.backend.user.entity.UserRole;
import com.lootfast.backend.user.entity.UserStatus;
import com.lootfast.backend.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpServiceImplTests {

    private static final String EMAIL = "user@gmail.com";
    private static final String SECRET = "test-otp-hmac-secret-that-is-at-least-32-characters";

    @Mock private OtpStore otpStore;
    @Mock private EmailSender emailSender;
    @Mock private UserRepository userRepository;

    private OtpServiceImpl otpService;

    @BeforeEach
    void setUp() {
        otpService = new OtpServiceImpl(otpStore, emailSender, userRepository, SECRET);
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void signupOtpStartsCooldownBeforeSendingEmail() {
        when(userRepository.findByEmailForUpdate(EMAIL))
                .thenReturn(Optional.of(user(UserStatus.UNVERIFIED)));
        when(otpStore.saveInitialOtpIfAllowed(eq(EMAIL), anyString())).thenReturn(true);

        otpService.sendSignupOtp(" User@Gmail.com ");

        verify(otpStore).saveInitialOtpIfAllowed(eq(EMAIL), anyString());
        verify(emailSender).sendOtp(eq(EMAIL), anyString());
    }

    @Test
    void signupOtpDoesNotOverwriteCodeWhenCooldownAlreadyExists() {
        when(userRepository.findByEmailForUpdate(EMAIL))
                .thenReturn(Optional.of(user(UserStatus.UNVERIFIED)));
        when(otpStore.saveInitialOtpIfAllowed(eq(EMAIL), anyString())).thenReturn(false);

        otpService.sendSignupOtp(EMAIL);

        verifyNoInteractions(emailSender);
    }

    @Test
    void successfulVerificationDeletesOtpOnlyAfterCommit() {
        User user = user(UserStatus.UNVERIFIED);
        when(userRepository.findByEmailForUpdate(EMAIL)).thenReturn(Optional.of(user));
        when(otpStore.checkOtp(eq(EMAIL), anyString(), eq(5)))
                .thenReturn(OtpCheckResult.match());
        TransactionSynchronizationManager.initSynchronization();

        otpService.verifyOtp(EMAIL, "123456");

        assertEquals(UserStatus.VERIFIED, user.getStatus());
        verify(userRepository).save(user);
        verify(otpStore, never()).deleteOtp(EMAIL);

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        verify(otpStore).deleteOtp(EMAIL);
    }

    @Test
    void databaseFailureDoesNotConsumeValidOtp() {
        User user = user(UserStatus.UNVERIFIED);
        when(userRepository.findByEmailForUpdate(EMAIL)).thenReturn(Optional.of(user));
        when(otpStore.checkOtp(eq(EMAIL), anyString(), eq(5)))
                .thenReturn(OtpCheckResult.match());
        when(userRepository.save(user)).thenThrow(new IllegalStateException("database unavailable"));
        TransactionSynchronizationManager.initSynchronization();

        assertThrows(IllegalStateException.class,
                () -> otpService.verifyOtp(EMAIL, "123456"));

        assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty());
        verify(otpStore, never()).deleteOtp(EMAIL);
    }

    @Test
    void invalidOtpReportsRemainingAttempts() {
        when(userRepository.findByEmailForUpdate(EMAIL))
                .thenReturn(Optional.of(user(UserStatus.UNVERIFIED)));
        when(otpStore.checkOtp(eq(EMAIL), anyString(), eq(5)))
                .thenReturn(OtpCheckResult.invalid(3));

        ApiException exception = assertThrows(ApiException.class,
                () -> otpService.verifyOtp(EMAIL, "000000"));

        assertEquals(ErrorCode.OTP_INVALID, exception.getErrorCode());
        assertEquals("Invalid OTP. 3 attempts remaining", exception.getMessage());
    }

    @Test
    void missingAndVerifiedUsersReceiveTheSameVerificationError() {
        when(userRepository.findByEmailForUpdate(EMAIL))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(user(UserStatus.VERIFIED)));

        ApiException missing = assertThrows(ApiException.class,
                () -> otpService.verifyOtp(EMAIL, "123456"));
        ApiException verified = assertThrows(ApiException.class,
                () -> otpService.verifyOtp(EMAIL, "123456"));

        assertEquals(ErrorCode.OTP_EXPIRED, missing.getErrorCode());
        assertEquals(missing.getMessage(), verified.getMessage());
        verifyNoInteractions(otpStore);
    }

    @Test
    void throttledResendReturnsSilentlyWithoutLookingUpAccount() {
        when(otpStore.reserveResend(EMAIL, 3)).thenReturn(ResendPermit.COOLDOWN_ACTIVE);

        otpService.resendOtp(EMAIL);

        verifyNoInteractions(userRepository, emailSender);
    }

    @Test
    void allowedResendForUnknownAccountStillReturnsSilently() {
        when(otpStore.reserveResend(EMAIL, 3)).thenReturn(ResendPermit.ALLOWED);
        when(userRepository.findByEmailForUpdate(EMAIL)).thenReturn(Optional.empty());

        otpService.resendOtp(EMAIL);

        verify(otpStore, never()).saveOtp(eq(EMAIL), anyString());
        verifyNoInteractions(emailSender);
    }

    @Test
    void allowedResendReplacesOtpForUnverifiedAccount() {
        when(otpStore.reserveResend(EMAIL, 3)).thenReturn(ResendPermit.ALLOWED);
        when(userRepository.findByEmailForUpdate(EMAIL))
                .thenReturn(Optional.of(user(UserStatus.UNVERIFIED)));

        otpService.resendOtp(EMAIL);

        verify(otpStore).saveOtp(eq(EMAIL), anyString());
        verify(emailSender).sendOtp(eq(EMAIL), anyString());
    }

    private User user(UserStatus status) {
        return new User(
                "An",
                "Nguyen",
                EMAIL,
                "912345678",
                "encoded-password",
                UserRole.BUYER,
                status
        );
    }
}
