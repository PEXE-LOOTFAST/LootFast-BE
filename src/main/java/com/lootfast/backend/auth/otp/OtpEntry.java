package com.lootfast.backend.auth.otp;

public record OtpEntry(String otpHash, int attempts) {
}
