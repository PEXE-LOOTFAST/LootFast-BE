package com.lootfast.backend.common.email;

public interface EmailSender {
    void sendOtp(String to, String otp);
}
