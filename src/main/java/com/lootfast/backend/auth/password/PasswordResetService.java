package com.lootfast.backend.auth.password;

import com.lootfast.backend.auth.dto.PasswordResetConfirmRequest;
import com.lootfast.backend.auth.otp.OtpCheckResult;
import com.lootfast.backend.auth.otp.ResendPermit;
import com.lootfast.backend.auth.session.AuthSessionRepository;
import com.lootfast.backend.common.email.EmailSender;
import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.user.entity.User;
import com.lootfast.backend.user.entity.UserStatus;
import com.lootfast.backend.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class PasswordResetService {

    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_REQUESTS = 3;

    private final PasswordResetOtpStore otpStore;
    private final UserRepository userRepository;
    private final AuthSessionRepository authSessionRepository;
    private final EmailSender emailSender;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final String hmacSecret;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetService(
            PasswordResetOtpStore otpStore,
            UserRepository userRepository,
            AuthSessionRepository authSessionRepository,
            EmailSender emailSender,
            PasswordEncoder passwordEncoder,
            Clock clock,
            @Value("${security.otp.hmac-secret}") String hmacSecret
    ) {
        this.otpStore = otpStore;
        this.userRepository = userRepository;
        this.authSessionRepository = authSessionRepository;
        this.emailSender = emailSender;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.hmacSecret = hmacSecret;
    }

    public void requestReset(String email) {
        String normalizedEmail = normalize(email);
        ResendPermit permit = otpStore.reserveRequest(normalizedEmail, MAX_REQUESTS);
        if (permit != ResendPermit.ALLOWED) {
            return;
        }

        User user = userRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null);
        if (user == null || user.getStatus() != UserStatus.VERIFIED) {
            return;
        }

        String otp = generateOtp();
        otpStore.saveOtp(normalizedEmail, hmac(otp));
        emailSender.sendPasswordResetOtp(normalizedEmail, otp);
    }

    @Transactional
    public void confirmReset(PasswordResetConfirmRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new ApiException(
                    ErrorCode.PASSWORD_MISMATCH,
                    "Password confirmation does not match"
            );
        }

        String normalizedEmail = normalize(request.email());
        User user = userRepository.findByEmailForUpdate(normalizedEmail).orElse(null);
        OtpCheckResult result = otpStore.consumeOtp(
                normalizedEmail,
                hmac(request.otp().trim()),
                MAX_ATTEMPTS
        );
        if (user == null || user.getStatus() != UserStatus.VERIFIED) {
            throw invalidOrExpiredOtp();
        }

        switch (result.status()) {
            case EXPIRED -> throw invalidOrExpiredOtp();
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
                user.changePassword(passwordEncoder.encode(request.newPassword()));
                authSessionRepository.revokeActiveSessions(user.getId(), clock.instant());
            }
        }
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String generateOtp() {
        return String.valueOf(secureRandom.nextInt(900_000) + 100_000);
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    hmacSecret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            ));
            return HexFormat.of().formatHex(
                    mac.doFinal(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("HMAC computation failed", exception);
        }
    }

    private ApiException invalidOrExpiredOtp() {
        return new ApiException(
                ErrorCode.OTP_EXPIRED,
                "OTP has expired or does not exist"
        );
    }
}
