package com.lootfast.backend.auth.otp;

import java.util.Optional;

public interface OtpStore {

    void saveOtp(String email, String otpHash);

    Optional<OtpEntry> findOtp(String email);

    // Atomic increment — trả về số attempts mới sau khi tăng
    long incrementAttempts(String email);

    void deleteOtp(String email);

    // Resend cooldown: 60 giây giữa 2 lần gửi
    boolean isResendCooldownActive(String email);

    void setResendCooldown(String email);

    // Resend count: tối đa 3 lần trong 15 phút
    long incrementResendCount(String email);
}
