package com.lootfast.backend.auth.password;

import com.lootfast.backend.auth.otp.OtpCheckResult;
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

@Testcontainers(disabledWithoutDocker = true)
class RedisPasswordResetOtpStoreIntegrationTests {

    private static final String EMAIL = "reset@gmail.com";

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine")
    ).withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static RedisPasswordResetOtpStore otpStore;

    @BeforeAll
    static void setUpRedisClient() {
        connectionFactory = new LettuceConnectionFactory(
                REDIS.getHost(),
                REDIS.getMappedPort(6379)
        );
        connectionFactory.afterPropertiesSet();
        StringRedisTemplate redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
        otpStore = new RedisPasswordResetOtpStore(redis);
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
    void concurrentConfirmationConsumesAnOtpExactlyOnce() throws Exception {
        otpStore.saveOtp(EMAIL, "correct-hash");
        int requestCount = 8;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<OtpCheckResult.Status>> futures = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(requestCount)) {
            for (int request = 0; request < requestCount; request++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return otpStore.consumeOtp(EMAIL, "correct-hash", 5).status();
                }));
            }
            start.countDown();

            long matches = 0;
            for (Future<OtpCheckResult.Status> future : futures) {
                if (future.get() == OtpCheckResult.Status.MATCH) {
                    matches++;
                }
            }
            assertEquals(1, matches);
        }
    }
}
