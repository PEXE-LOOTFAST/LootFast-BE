package com.lootfast.backend.auth.service;

public interface OtpService {

    void sendOtp(String email);

    void verifyOtp(String email, String otp);

    void resendOtp(String email);
}
