package com.lootfast.backend.auth.service;

import com.lootfast.backend.auth.otp.OtpEntry;
import com.lootfast.backend.auth.otp.OtpStore;
import com.lootfast.backend.common.email.EmailSender;
import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.user.entity.UserStatus;
import com.lootfast.backend.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_RESEND = 3;

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
    public void sendOtp(String email) {
        String otp = generateOtp();
        otpStore.saveOtp(email, hmac(otp));
        emailSender.sendOtp(email, otp); // @Async bên trong — không block
    }

    @Override
    @Transactional
    public void verifyOtp(String email, String otp) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        OtpEntry entry = otpStore.findOtp(normalizedEmail)
                .orElseThrow(() -> new ApiException(ErrorCode.OTP_EXPIRED,
                        "OTP has expired or does not exist"));

        if (!hmac(otp.trim()).equals(entry.otpHash())) {
            long attempts = otpStore.incrementAttempts(normalizedEmail);
            if (attempts >= MAX_ATTEMPTS) {
                otpStore.deleteOtp(normalizedEmail);
                throw new ApiException(ErrorCode.OTP_INVALID,
                        "Too many failed attempts. Please request a new OTP");
            }
            long remaining = MAX_ATTEMPTS - attempts;
            throw new ApiException(ErrorCode.OTP_INVALID,
                    "Invalid OTP. " + remaining + " attempt" + (remaining == 1 ? "" : "s") + " remaining");
        }

        // OTP đúng — xoá ngay để chặn replay trong thời gian TTL còn lại
        otpStore.deleteOtp(normalizedEmail);

        userRepository.findByEmailIgnoreCase(normalizedEmail).ifPresent(user -> {
            user.verify();
            userRepository.save(user);
        });
    }

    @Override
    public void resendOtp(String email) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        // Trả về silent nếu email không tồn tại hoặc đã VERIFIED — tránh email enumeration
        boolean needsVerification = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .map(u -> u.getStatus() == UserStatus.UNVERIFIED)
                .orElse(false);

        if (!needsVerification) return;

        if (otpStore.isResendCooldownActive(normalizedEmail)) {
            throw new ApiException(ErrorCode.OTP_RATE_LIMIT,
                    "Please wait 60 seconds before requesting a new OTP");
        }

        long count = otpStore.incrementResendCount(normalizedEmail);
        if (count > MAX_RESEND) {
            throw new ApiException(ErrorCode.OTP_RATE_LIMIT,
                    "Maximum resend attempts reached. Please try again in 15 minutes");
        }

        otpStore.deleteOtp(normalizedEmail);
        otpStore.setResendCooldown(normalizedEmail);
        sendOtp(normalizedEmail);
    }

    private String generateOtp() {
        // 100000–999999: đảm bảo luôn đủ 6 chữ số, không có leading zero
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
