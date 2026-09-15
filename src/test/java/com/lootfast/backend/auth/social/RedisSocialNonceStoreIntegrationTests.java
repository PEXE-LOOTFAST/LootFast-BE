package com.lootfast.backend.auth.social;

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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class RedisSocialNonceStoreIntegrationTests {

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine")
    ).withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static RedisSocialNonceStore nonceStore;

    @BeforeAll
    static void setUpRedisClient() {
        connectionFactory = new LettuceConnectionFactory(
                REDIS.getHost(),
                REDIS.getMappedPort(6379)
        );
        connectionFactory.afterPropertiesSet();
        StringRedisTemplate redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
        nonceStore = new RedisSocialNonceStore(redis);
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
    void concurrentLoginAttemptsConsumeNonceExactlyOnce() throws Exception {
        String nonce = "single-use-nonce";
        assertTrue(nonceStore.save(nonce, Duration.ofMinutes(5)));
        int requestCount = 8;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(requestCount)) {
            for (int request = 0; request < requestCount; request++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return nonceStore.consume(nonce);
                }));
            }
            start.countDown();

            long consumed = 0;
            for (Future<Boolean> future : futures) {
                if (future.get()) {
                    consumed++;
                }
            }
            assertEquals(1, consumed);
        }
    }
}
