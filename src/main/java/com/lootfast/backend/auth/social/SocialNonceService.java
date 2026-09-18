package com.lootfast.backend.auth.social;

import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

@Service
public class SocialNonceService {

    private static final Duration NONCE_TTL = Duration.ofMinutes(5);
    private static final int MAX_GENERATION_ATTEMPTS = 3;

    private final SocialNonceStore nonceStore;
    private final SecureRandom secureRandom = new SecureRandom();

    SocialNonceService(SocialNonceStore nonceStore) {
        this.nonceStore = nonceStore;
    }

    public IssuedNonce issue() {
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            byte[] random = new byte[32];
            secureRandom.nextBytes(random);
            String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
            if (nonceStore.save(nonce, NONCE_TTL)) {
                return new IssuedNonce(nonce, NONCE_TTL.toSeconds());
            }
        }
        throw new ApiException(
                ErrorCode.SOCIAL_LOGIN_UNAVAILABLE,
                "Could not create a social login nonce"
        );
    }

    public void consume(String nonce) {
        if (!nonceStore.consume(nonce)) {
            throw new ApiException(
                    ErrorCode.INVALID_SOCIAL_NONCE,
                    "The social login nonce is invalid, expired, or already used"
            );
        }
    }

    public record IssuedNonce(String nonce, long expiresIn) {
    }
}
