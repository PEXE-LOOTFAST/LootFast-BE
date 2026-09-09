package com.lootfast.backend.auth.otp;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class RedisOtpStoreIntegrationTests {

    private static final String EMAIL = "concurrent@gmail.com";

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine")
    ).withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static RedisOtpStore otpStore;

    @BeforeAll
    static void setUpRedisClient() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        otpStore = new RedisOtpStore(redisTemplate);
    }

    @AfterAll
    static void closeRedisClient() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @BeforeEach
    void clearRedis() {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            connection.serverCommands().flushDb();
        }
    }

    @Test
    void initialOtpAndCooldownAreCreatedAtomically() {
        assertTrue(otpStore.saveInitialOtpIfAllowed(EMAIL, "first-hash"));
        assertFalse(otpStore.saveInitialOtpIfAllowed(EMAIL, "second-hash"));

        assertEquals(OtpCheckResult.Status.MATCH,
                otpStore.checkOtp(EMAIL, "first-hash", 5).status());
        assertEquals(OtpCheckResult.Status.INVALID,
                otpStore.checkOtp(EMAIL, "second-hash", 5).status());
    }

    @Test
    void failedAttemptLimitDeletesOtpAtomically() {
        otpStore.saveOtp(EMAIL, "correct-hash");

        assertEquals(4, otpStore.checkOtp(EMAIL, "wrong-hash", 5).attemptsRemaining());
        assertEquals(3, otpStore.checkOtp(EMAIL, "wrong-hash", 5).attemptsRemaining());
        assertEquals(2, otpStore.checkOtp(EMAIL, "wrong-hash", 5).attemptsRemaining());
        assertEquals(1, otpStore.checkOtp(EMAIL, "wrong-hash", 5).attemptsRemaining());
        assertEquals(OtpCheckResult.Status.ATTEMPTS_EXHAUSTED,
                otpStore.checkOtp(EMAIL, "wrong-hash", 5).status());
        assertEquals(OtpCheckResult.Status.EXPIRED,
                otpStore.checkOtp(EMAIL, "correct-hash", 5).status());
    }

    @Test
    void concurrentResendsReceiveOnlyOnePermit() throws Exception {
        int requestCount = 12;
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ResendPermit>> futures = new ArrayList<>();

        try {
            for (int index = 0; index < requestCount; index++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return otpStore.reserveResend(EMAIL, 3);
                }));
            }
            start.countDown();

            long allowed = 0;
            for (Future<ResendPermit> future : futures) {
                if (future.get() == ResendPermit.ALLOWED) {
                    allowed++;
                }
            }
            assertEquals(1, allowed);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void resendLimitIsEnforcedWithinWindow() {
        String cooldownKey = "otp:resend:cooldown:" + EMAIL;

        for (int resend = 0; resend < 3; resend++) {
            assertEquals(ResendPermit.ALLOWED, otpStore.reserveResend(EMAIL, 3));
            redisTemplate.delete(cooldownKey);
        }

        assertEquals(ResendPermit.LIMIT_REACHED, otpStore.reserveResend(EMAIL, 3));
    }
}
