package com.lootfast.backend.order.service;

import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import java.time.Duration;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class MerchantPickupConfirmationRateLimiter {
  static final int MAX_ATTEMPTS_PER_WINDOW = 30;
  static final Duration WINDOW = Duration.ofMinutes(1);

  private static final String KEY_PREFIX = "rate:pickup-confirmation:";
  private static final DefaultRedisScript<Long> RESERVE_ATTEMPT_SCRIPT =
      new DefaultRedisScript<>(
          """
          local current = redis.call('INCR', KEYS[1])
          if current == 1 then
              redis.call('EXPIRE', KEYS[1], ARGV[1])
          end
          return current
          """,
          Long.class);

  private final StringRedisTemplate redis;

  public MerchantPickupConfirmationRateLimiter(StringRedisTemplate redis) {
    this.redis = redis;
  }

  public void requireAllowed(Long merchantUserId) {
    try {
      Long count =
          redis.execute(
              RESERVE_ATTEMPT_SCRIPT,
              List.of(KEY_PREFIX + merchantUserId),
              String.valueOf(WINDOW.toSeconds()));
      if (count == null) throw unavailable();
      if (count > MAX_ATTEMPTS_PER_WINDOW) {
        throw new ApiException(
            ErrorCode.PICKUP_CONFIRMATION_RATE_LIMITED,
            "Too many pickup confirmation attempts; retry in one minute");
      }
    } catch (DataAccessException exception) {
      throw unavailable();
    }
  }

  private ApiException unavailable() {
    return new ApiException(
        ErrorCode.PICKUP_CONFIRMATION_UNAVAILABLE,
        "Pickup confirmation is temporarily unavailable");
  }
}
