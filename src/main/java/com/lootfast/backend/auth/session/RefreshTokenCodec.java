package com.lootfast.backend.auth.session;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Component
public class RefreshTokenCodec {

    private static final int SECRET_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    public IssuedRefreshToken issue(UUID sessionId) {
        byte[] secret = new byte[SECRET_BYTES];
        secureRandom.nextBytes(secret);

        String encodedSecret = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        String value = sessionId + "." + encodedSecret;
        return new IssuedRefreshToken(value, hash(value));
    }

    public UUID parseSessionId(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Refresh token is missing");
        }

        int separator = value.indexOf('.');
        if (separator <= 0 || separator != value.lastIndexOf('.') || separator == value.length() - 1) {
            throw new IllegalArgumentException("Refresh token has an invalid format");
        }

        return UUID.fromString(value.substring(0, separator));
    }

    public boolean matches(String rawToken, String expectedHash) {
        if (rawToken == null || expectedHash == null) {
            return false;
        }

        byte[] actual = hash(rawToken).getBytes(StandardCharsets.US_ASCII);
        byte[] expected = expectedHash.getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(actual, expected);
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    public record IssuedRefreshToken(String value, String hash) {
    }
}
