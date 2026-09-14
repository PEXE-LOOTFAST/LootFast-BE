package com.lootfast.backend.auth.event;

import com.lootfast.backend.auth.service.OtpService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SignupOtpListenerTests {

    @Test
    void listenerIssuesOtpAfterReceivingCommittedSignupEvent() {
        OtpService otpService = mock(OtpService.class);
        SignupOtpListener listener = new SignupOtpListener(otpService);

        listener.sendVerificationCode(new SignupCreatedEvent("user@gmail.com"));

        verify(otpService).sendSignupOtp("user@gmail.com");
    }

    @Test
    void deliveryFailureDoesNotTurnCommittedSignupIntoFailure() {
        OtpService otpService = mock(OtpService.class);
        doThrow(new IllegalStateException("Redis unavailable"))
                .when(otpService).sendSignupOtp("user@gmail.com");
        SignupOtpListener listener = new SignupOtpListener(otpService);

        assertDoesNotThrow(() -> listener.sendVerificationCode(
                new SignupCreatedEvent("user@gmail.com")));
    }
}
