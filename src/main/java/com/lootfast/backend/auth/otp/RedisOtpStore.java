package com.lootfast.backend.auth.otp;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

@Repository
class RedisOtpStore implements OtpStore {

    private static final Duration OTP_TTL = Duration.ofMinutes(5);
    private static final Duration COOLDOWN_TTL = Duration.ofSeconds(60);
    private static final Duration RESEND_WINDOW_TTL = Duration.ofMinutes(15);

    private static final String OTP_PREFIX = "otp:register:";
    private static final String COOLDOWN_PREFIX = "otp:resend:cooldown:";
    private static final String RESEND_COUNT_PREFIX = "otp:resend:count:";

    private static final String FIELD_HASH = "hash";
    private static final String FIELD_ATTEMPTS = "attempts";

    private final StringRedisTemplate redis;

    RedisOtpStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void saveOtp(String email, String otpHash) {
        String key = OTP_PREFIX + email;
        redis.opsForHash().putAll(key, Map.of(
                FIELD_HASH, otpHash,
                FIELD_ATTEMPTS, "0"
        ));
        redis.expire(key, OTP_TTL);
    }

    @Override
    public Optional<OtpEntry> findOtp(String email) {
        Map<Object, Object> fields = redis.opsForHash().entries(OTP_PREFIX + email);
        if (fields.isEmpty()) return Optional.empty();
        String hash = (String) fields.get(FIELD_HASH);
        int attempts = Integer.parseInt((String) fields.get(FIELD_ATTEMPTS));
        return Optional.of(new OtpEntry(hash, attempts));
    }

    @Override
    public long incrementAttempts(String email) {
        // HINCRBY atomic — không reset TTL của key
        Long result = redis.opsForHash().increment(OTP_PREFIX + email, FIELD_ATTEMPTS, 1);
        return result != null ? result : 1;
    }

    @Override
    public void deleteOtp(String email) {
        redis.delete(OTP_PREFIX + email);
    }

    @Override
    public boolean isResendCooldownActive(String email) {
        return Boolean.TRUE.equals(redis.hasKey(COOLDOWN_PREFIX + email));
    }

    @Override
    public void setResendCooldown(String email) {
        redis.opsForValue().set(COOLDOWN_PREFIX + email, "1", COOLDOWN_TTL);
    }

    @Override
    public long incrementResendCount(String email) {
        String key = RESEND_COUNT_PREFIX + email;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) {
            // Lần đầu increment — đặt TTL 15 phút (window)
            redis.expire(key, RESEND_WINDOW_TTL);
        }
        return count != null ? count : 1;
    }
}
