package com.lootfast.backend.auth.event;

import com.lootfast.backend.auth.service.OtpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
class SignupOtpListener {

    private static final Logger log = LoggerFactory.getLogger(SignupOtpListener.class);

    private final OtpService otpService;

    SignupOtpListener(OtpService otpService) {
        this.otpService = otpService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendVerificationCode(SignupCreatedEvent event) {
        try {
            otpService.sendSignupOtp(event.email());
        } catch (RuntimeException ex) {
            // The user is already committed. Keep signup successful and let resend retry delivery.
            log.error("Failed to issue signup OTP after user commit", ex);
        }
    }
}
