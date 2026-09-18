package com.lootfast.backend.auth.password;

import com.lootfast.backend.auth.otp.OtpCheckResult;
import com.lootfast.backend.auth.otp.ResendPermit;

interface PasswordResetOtpStore {

    ResendPermit reserveRequest(String email, int maxRequests);

    void saveOtp(String email, String otpHash);

    OtpCheckResult consumeOtp(String email, String otpHash, int maxAttempts);
}
