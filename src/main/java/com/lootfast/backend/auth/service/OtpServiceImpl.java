package com.lootfast.backend.auth.service;

import com.lootfast.backend.auth.otp.OtpCheckResult;
import com.lootfast.backend.auth.otp.OtpStore;
import com.lootfast.backend.auth.otp.ResendPermit;
import com.lootfast.backend.common.email.EmailSender;
import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.user.entity.User;
import com.lootfast.backend.user.entity.UserStatus;
import com.lootfast.backend.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;

@Service
class OtpServiceImpl implements OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpServiceImpl.class);
    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_RESENDS = 3;

    private final OtpStore otpStore;
    private final EmailSender emailSender;
    private final UserRepository userRepository;
    private final String hmacSecret;
    private final SecureRandom secureRandom = new SecureRandom();

    OtpServiceImpl(OtpStore otpStore,
                   EmailSender emailSender,
                   UserRepository userRepository,
                   @Value("${security.otp.hmac-secret}") String hmacSecret) {
        this.otpStore = otpStore;
        this.emailSender = emailSender;
        this.userRepository = userRepository;
        this.hmacSecret = hmacSecret;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendSignupOtp(String email) {
        String normalizedEmail = normalize(email);
        User user = userRepository.findByEmailForUpdate(normalizedEmail).orElse(null);
        if (user == null || user.getStatus() != UserStatus.UNVERIFIED) {
            return;
        }

        String otp = generateOtp();
        if (otpStore.saveInitialOtpIfAllowed(normalizedEmail, hmac(otp))) {
            emailSender.sendOtp(normalizedEmail, otp);
        }
    }

    @Override
    @Transactional
    public void verifyOtp(String email, String otp) {
        String normalizedEmail = normalize(email);
        User user = userRepository.findByEmailForUpdate(normalizedEmail)
                .filter(candidate -> candidate.getStatus() == UserStatus.UNVERIFIED)
                .orElseThrow(this::expiredOtp);

        OtpCheckResult result = otpStore.checkOtp(
                normalizedEmail,
                hmac(otp.trim()),
                MAX_ATTEMPTS
        );

        switch (result.status()) {
            case EXPIRED -> throw expiredOtp();
            case ATTEMPTS_EXHAUSTED -> throw new ApiException(
                    ErrorCode.OTP_INVALID,
                    "Too many failed attempts. Please request a new OTP"
            );
            case INVALID -> throw new ApiException(
                    ErrorCode.OTP_INVALID,
                    "Invalid OTP. " + result.attemptsRemaining() + " attempt"
                            + (result.attemptsRemaining() == 1 ? "" : "s") + " remaining"
            );
            case MATCH -> {
                user.verify();
                userRepository.save(user);
                deleteOtpAfterCommit(normalizedEmail);
            }
        }
    }

    @Override
    @Transactional
    public void resendOtp(String email) {
        String normalizedEmail = normalize(email);

        // Throttle every normalized email identically. The public response remains 200,
        // so callers cannot infer whether an unverified account exists.
        ResendPermit permit = otpStore.reserveResend(normalizedEmail, MAX_RESENDS);
        if (permit != ResendPermit.ALLOWED) {
            return;
        }

        User user = userRepository.findByEmailForUpdate(normalizedEmail).orElse(null);
        if (user == null || user.getStatus() != UserStatus.UNVERIFIED) {
            return;
        }

        String otp = generateOtp();
        otpStore.saveOtp(normalizedEmail, hmac(otp));
        emailSender.sendOtp(normalizedEmail, otp);
    }

    private void deleteOtpAfterCommit(String email) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("OTP verification requires an active transaction");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    otpStore.deleteOtp(email);
                } catch (RuntimeException ex) {
                    // The account is already VERIFIED, so a leftover code cannot be replayed.
                    log.warn("Could not delete consumed OTP after account verification", ex);
                }
            }
        });
    }

    private ApiException expiredOtp() {
        return new ApiException(ErrorCode.OTP_EXPIRED, "OTP has expired or does not exist");
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String generateOtp() {
        return String.valueOf(secureRandom.nextInt(900_000) + 100_000);
    }

    private String hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    hmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(
                    mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC computation failed", e);
        }
    }
}
