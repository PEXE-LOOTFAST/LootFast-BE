package com.lootfast.backend.auth.otp;

public interface OtpStore {

    void saveOtp(String email, String otpHash);

    boolean saveInitialOtpIfAllowed(String email, String otpHash);

    OtpCheckResult checkOtp(String email, String otpHash, int maxAttempts);

    ResendPermit reserveResend(String email, int maxResends);

    void deleteOtp(String email);
}
