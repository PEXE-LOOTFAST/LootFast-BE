package com.lootfast.backend.auth.service;

public interface OtpService {

    void sendSignupOtp(String email);

    void verifyOtp(String email, String otp);

    void resendOtp(String email);
}
