package com.lootfast.backend.merchant.service;

import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class MerchantImageUploadRateLimiter {

    static final int MAX_UPLOADS_PER_WINDOW = 10;
    static final Duration WINDOW = Duration.ofHours(1);

    private static final String KEY_PREFIX = "rate:merchant-image-upload:";
    private static final DefaultRedisScript<Long> RESERVE_UPLOAD_SCRIPT =
            new DefaultRedisScript<>("""
                    local current = redis.call('INCR', KEYS[1])
                    if current == 1 then
                        redis.call('EXPIRE', KEYS[1], ARGV[1])
                    end
                    return current
                    """, Long.class);

    private final StringRedisTemplate redis;

    public MerchantImageUploadRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void requireAllowed(Long userId) {
        try {
            Long count = redis.execute(
                    RESERVE_UPLOAD_SCRIPT,
                    List.of(KEY_PREFIX + userId),
                    String.valueOf(WINDOW.toSeconds())
            );
            if (count == null) {
                throw unavailable();
            }
            if (count > MAX_UPLOADS_PER_WINDOW) {
                throw new ApiException(
                        ErrorCode.MERCHANT_IMAGE_RATE_LIMITED,
                        "Merchant logo and cover images can be uploaded at most 10 times per hour"
                );
            }
        } catch (DataAccessException exception) {
            throw unavailable();
        }
    }

    private ApiException unavailable() {
        return new ApiException(
                ErrorCode.MERCHANT_IMAGE_RATE_LIMIT_UNAVAILABLE,
                "Merchant image upload is temporarily unavailable"
        );
    }
}
