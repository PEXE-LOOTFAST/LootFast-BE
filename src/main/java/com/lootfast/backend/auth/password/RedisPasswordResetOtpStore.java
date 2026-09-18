package com.lootfast.backend.auth.password;

import com.lootfast.backend.auth.otp.OtpCheckResult;
import com.lootfast.backend.auth.otp.ResendPermit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;

@Repository
class RedisPasswordResetOtpStore implements PasswordResetOtpStore {

    private static final Duration OTP_TTL = Duration.ofMinutes(5);
    private static final Duration COOLDOWN_TTL = Duration.ofSeconds(60);
    private static final Duration REQUEST_WINDOW_TTL = Duration.ofMinutes(15);
    private static final String OTP_PREFIX = "otp:password-reset:";
    private static final String COOLDOWN_PREFIX = "otp:password-reset:cooldown:";
    private static final String COUNT_PREFIX = "otp:password-reset:count:";

    private static final DefaultRedisScript<Long> RESERVE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 1 then
                return 1
            end
            local count = tonumber(redis.call('GET', KEYS[2]) or '0')
            if count >= tonumber(ARGV[3]) then
                return 2
            end
            redis.call('SET', KEYS[1], '1', 'EX', ARGV[1])
            count = redis.call('INCR', KEYS[2])
            if count == 1 then
                redis.call('EXPIRE', KEYS[2], ARGV[2])
            end
            return 0
            """, Long.class);

    private static final DefaultRedisScript<Long> SAVE_SCRIPT = new DefaultRedisScript<>("""
            redis.call('HSET', KEYS[1], 'hash', ARGV[1], 'attempts', '0')
            redis.call('EXPIRE', KEYS[1], ARGV[2])
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> CONSUME_SCRIPT = new DefaultRedisScript<>("""
            local storedHash = redis.call('HGET', KEYS[1], 'hash')
            if not storedHash then
                return -1
            end
            if storedHash == ARGV[1] then
                redis.call('DEL', KEYS[1])
                return 0
            end
            local attempts = redis.call('HINCRBY', KEYS[1], 'attempts', 1)
            if attempts >= tonumber(ARGV[2]) then
                redis.call('DEL', KEYS[1])
                return -2
            end
            return tonumber(ARGV[2]) - attempts
            """, Long.class);

    private final StringRedisTemplate redis;

    RedisPasswordResetOtpStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public ResendPermit reserveRequest(String email, int maxRequests) {
        Long result = redis.execute(
                RESERVE_SCRIPT,
                List.of(cooldownKey(email), countKey(email)),
                String.valueOf(COOLDOWN_TTL.toSeconds()),
                String.valueOf(REQUEST_WINDOW_TTL.toSeconds()),
                String.valueOf(maxRequests)
        );
        if (Long.valueOf(0).equals(result)) {
            return ResendPermit.ALLOWED;
        }
        if (Long.valueOf(1).equals(result)) {
            return ResendPermit.COOLDOWN_ACTIVE;
        }
        return ResendPermit.LIMIT_REACHED;
    }

    @Override
    public void saveOtp(String email, String otpHash) {
        redis.execute(
                SAVE_SCRIPT,
                List.of(otpKey(email)),
                otpHash,
                String.valueOf(OTP_TTL.toSeconds())
        );
    }

    @Override
    public OtpCheckResult consumeOtp(String email, String otpHash, int maxAttempts) {
        Long result = redis.execute(
                CONSUME_SCRIPT,
                List.of(otpKey(email)),
                otpHash,
                String.valueOf(maxAttempts)
        );
        if (result == null || result == -1) {
            return OtpCheckResult.expired();
        }
        if (result == 0) {
            return OtpCheckResult.match();
        }
        if (result == -2) {
            return OtpCheckResult.attemptsExhausted();
        }
        return OtpCheckResult.invalid(Math.toIntExact(result));
    }

    private String otpKey(String email) {
        return OTP_PREFIX + email;
    }

    private String cooldownKey(String email) {
        return COOLDOWN_PREFIX + email;
    }

    private String countKey(String email) {
        return COUNT_PREFIX + email;
    }
}
