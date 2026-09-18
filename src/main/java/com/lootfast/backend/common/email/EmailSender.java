package com.lootfast.backend.common.email;

public interface EmailSender {
    void sendSignupOtp(String to, String otp);

    void sendPasswordResetOtp(String to, String otp);
}
