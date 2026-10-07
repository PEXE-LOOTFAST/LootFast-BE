package com.lootfast.backend.merchant.performance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.lootfast.backend.common.exception.*;
import com.lootfast.backend.merchant.performance.service.AdvancedReportRateLimiter;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers
class AdvancedReportRateLimiterTests {
  @Container
  static final GenericContainer<?> REDIS =
      new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

  @Test
  void concurrentRequestsAcrossInstancesShareOneQuotaAndExpiry() throws Exception {
    var factory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
    factory.afterPropertiesSet();
    factory.start();
    var redis = new StringRedisTemplate(factory);
    redis.afterPropertiesSet();
    var a = new AdvancedReportRateLimiter(redis);
    var b = new AdvancedReportRateLimiter(redis);
    var pool = Executors.newFixedThreadPool(8);
    var gate = new CountDownLatch(1);
    long user = 12345L;
    try {
      var results = new ArrayList<Future<String>>();
      for (int i = 0; i < 25; i++) {
        var limiter = i % 2 == 0 ? a : b;
        results.add(
            pool.submit(
                () -> {
                  gate.await();
                  try {
                    limiter.requireAllowed(user);
                    return "OK";
                  } catch (ApiException e) {
                    return e.getErrorCode().name();
                  }
                }));
      }
      gate.countDown();
      var outcomes = new ArrayList<String>();
      for (var result : results) outcomes.add(result.get(10, TimeUnit.SECONDS));
      assertEquals(20, Collections.frequency(outcomes, "OK"));
      assertEquals(5, Collections.frequency(outcomes, "REPORT_RATE_LIMITED"));
      Long ttl = redis.getExpire("rate:merchant-advanced-report:" + user);
      assertNotNull(ttl);
      assertTrue(ttl > 0 && ttl <= 60);
    } finally {
      pool.shutdownNow();
      factory.destroy();
    }
  }

  @Test
  void redisFailureRejectsExpensiveReports() {
    var redis = mock(StringRedisTemplate.class);
    when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
        .thenThrow(new RedisConnectionFailureException("Test offline"));
    var error =
        assertThrows(
            ApiException.class, () -> new AdvancedReportRateLimiter(redis).requireAllowed(1));
    assertEquals(ErrorCode.RESOURCE_BUSY, error.getErrorCode());
  }
}
