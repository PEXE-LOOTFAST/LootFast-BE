package com.lootfast.backend.auth.social;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

@Repository
class RedisSocialNonceStore implements SocialNonceStore {

    private static final String KEY_PREFIX = "auth:social:nonce:";
    private static final DefaultRedisScript<Long> CONSUME_SCRIPT =
            new DefaultRedisScript<>("""
                    if redis.call('GET', KEYS[1]) then
                        redis.call('DEL', KEYS[1])
                        return 1
                    end
                    return 0
                    """, Long.class);

    private final StringRedisTemplate redis;

    RedisSocialNonceStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean save(String nonce, Duration ttl) {
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key(nonce), "1", ttl));
    }

    @Override
    public boolean consume(String nonce) {
        Long result = redis.execute(CONSUME_SCRIPT, List.of(key(nonce)));
        return Long.valueOf(1).equals(result);
    }

    private String key(String nonce) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(nonce.getBytes(StandardCharsets.UTF_8));
            return KEY_PREFIX + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
