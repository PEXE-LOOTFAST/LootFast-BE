package com.lootfast.backend.auth.otp;

public record OtpCheckResult(Status status, int attemptsRemaining) {

    public enum Status {
        MATCH,
        INVALID,
        EXPIRED,
        ATTEMPTS_EXHAUSTED
    }

    public static OtpCheckResult match() {
        return new OtpCheckResult(Status.MATCH, 0);
    }

    public static OtpCheckResult invalid(int attemptsRemaining) {
        return new OtpCheckResult(Status.INVALID, attemptsRemaining);
    }

    public static OtpCheckResult expired() {
        return new OtpCheckResult(Status.EXPIRED, 0);
    }

    public static OtpCheckResult attemptsExhausted() {
        return new OtpCheckResult(Status.ATTEMPTS_EXHAUSTED, 0);
    }
}
