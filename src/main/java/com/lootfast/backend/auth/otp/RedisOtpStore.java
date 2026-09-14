package com.lootfast.backend.auth.otp;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;

@Repository
class RedisOtpStore implements OtpStore {

    private static final Duration OTP_TTL = Duration.ofMinutes(5);
    private static final Duration COOLDOWN_TTL = Duration.ofSeconds(60);
    private static final Duration RESEND_WINDOW_TTL = Duration.ofMinutes(15);

    private static final String OTP_PREFIX = "otp:register:";
    private static final String COOLDOWN_PREFIX = "otp:resend:cooldown:";
    private static final String RESEND_COUNT_PREFIX = "otp:resend:count:";

    private static final long CHECK_MATCH = 0;
    private static final long CHECK_EXPIRED = -1;
    private static final long CHECK_EXHAUSTED = -2;

    private static final DefaultRedisScript<Long> SAVE_OTP_SCRIPT = new DefaultRedisScript<>("""
            redis.call('HSET', KEYS[1], 'hash', ARGV[1], 'attempts', '0')
            redis.call('EXPIRE', KEYS[1], ARGV[2])
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> SAVE_INITIAL_OTP_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[2]) == 1 then
                return 0
            end
            redis.call('HSET', KEYS[1], 'hash', ARGV[1], 'attempts', '0')
            redis.call('EXPIRE', KEYS[1], ARGV[2])
            redis.call('SET', KEYS[2], '1', 'EX', ARGV[3])
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> CHECK_OTP_SCRIPT = new DefaultRedisScript<>("""
            local storedHash = redis.call('HGET', KEYS[1], 'hash')
            if not storedHash then
                return -1
            end
            if storedHash == ARGV[1] then
                return 0
            end
            local attempts = redis.call('HINCRBY', KEYS[1], 'attempts', 1)
            if attempts >= tonumber(ARGV[2]) then
                redis.call('DEL', KEYS[1])
                return -2
            end
            return tonumber(ARGV[2]) - attempts
            """, Long.class);

    private static final DefaultRedisScript<Long> RESERVE_RESEND_SCRIPT = new DefaultRedisScript<>("""
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

    private final StringRedisTemplate redis;

    RedisOtpStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void saveOtp(String email, String otpHash) {
        redis.execute(
                SAVE_OTP_SCRIPT,
                List.of(otpKey(email)),
                otpHash,
                String.valueOf(OTP_TTL.toSeconds())
        );
    }

    @Override
    public boolean saveInitialOtpIfAllowed(String email, String otpHash) {
        Long result = redis.execute(
                SAVE_INITIAL_OTP_SCRIPT,
                List.of(otpKey(email), cooldownKey(email)),
                otpHash,
                String.valueOf(OTP_TTL.toSeconds()),
                String.valueOf(COOLDOWN_TTL.toSeconds())
        );
        return Long.valueOf(1).equals(result);
    }

    @Override
    public OtpCheckResult checkOtp(String email, String otpHash, int maxAttempts) {
        Long result = redis.execute(
                CHECK_OTP_SCRIPT,
                List.of(otpKey(email)),
                otpHash,
                String.valueOf(maxAttempts)
        );
        if (result == null || result == CHECK_EXPIRED) {
            return OtpCheckResult.expired();
        }
        if (result == CHECK_MATCH) {
            return OtpCheckResult.match();
        }
        if (result == CHECK_EXHAUSTED) {
            return OtpCheckResult.attemptsExhausted();
        }
        return OtpCheckResult.invalid(Math.toIntExact(result));
    }

    @Override
    public ResendPermit reserveResend(String email, int maxResends) {
        Long result = redis.execute(
                RESERVE_RESEND_SCRIPT,
                List.of(cooldownKey(email), resendCountKey(email)),
                String.valueOf(COOLDOWN_TTL.toSeconds()),
                String.valueOf(RESEND_WINDOW_TTL.toSeconds()),
                String.valueOf(maxResends)
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
    public void deleteOtp(String email) {
        redis.delete(otpKey(email));
    }

    private String otpKey(String email) {
        return OTP_PREFIX + email;
    }

    private String cooldownKey(String email) {
        return COOLDOWN_PREFIX + email;
    }

    private String resendCountKey(String email) {
        return RESEND_COUNT_PREFIX + email;
    }
}
