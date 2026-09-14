package com.lootfast.backend.merchant.service;

import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers(disabledWithoutDocker = true)
class MerchantImageUploadRateLimiterIntegrationTests {

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine")
    ).withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static MerchantImageUploadRateLimiter rateLimiter;

    @BeforeAll
    static void setUpRedisClient() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        StringRedisTemplate redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
        rateLimiter = new MerchantImageUploadRateLimiter(redis);
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
    void eleventhUploadIsRejectedWithinTheSharedWindow() {
        for (long request = 0; request < MerchantImageUploadRateLimiter.MAX_UPLOADS_PER_WINDOW;
             request++) {
            rateLimiter.requireAllowed(42L);
        }

        ApiException exception = assertThrows(
                ApiException.class,
                () -> rateLimiter.requireAllowed(42L)
        );

        assertEquals(ErrorCode.MERCHANT_IMAGE_RATE_LIMITED, exception.getErrorCode());
    }

    @Test
    void concurrentRequestsReceiveExactlyTheConfiguredNumberOfPermits() throws Exception {
        int requestCount = 20;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(requestCount)) {
            for (int request = 0; request < requestCount; request++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    try {
                        rateLimiter.requireAllowed(42L);
                        return true;
                    } catch (ApiException exception) {
                        assertEquals(
                                ErrorCode.MERCHANT_IMAGE_RATE_LIMITED,
                                exception.getErrorCode()
                        );
                        return false;
                    }
                }));
            }
            start.countDown();

            long allowed = 0;
            for (Future<Boolean> future : futures) {
                if (future.get()) {
                    allowed++;
                }
            }
            assertEquals(MerchantImageUploadRateLimiter.MAX_UPLOADS_PER_WINDOW, allowed);
        }
    }
}
