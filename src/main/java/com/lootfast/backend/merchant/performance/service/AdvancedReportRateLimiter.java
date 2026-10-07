package com.lootfast.backend.merchant.performance.service;

import com.lootfast.backend.common.exception.*;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class AdvancedReportRateLimiter {
  private final StringRedisTemplate redis;
  private static final DefaultRedisScript<Long> LIMIT =
      new DefaultRedisScript<>(
          "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],60) end;"
              + " return n",
          Long.class);

  public AdvancedReportRateLimiter(StringRedisTemplate redis) {
    this.redis = redis;
  }

  public void requireAllowed(long user) {
    try {
      Long n = redis.execute(LIMIT, List.of("rate:merchant-advanced-report:" + user));
      if (n == null)
        throw new ApiException(ErrorCode.RESOURCE_BUSY, "Report rate limiter unavailable");
      if (n > 20)
        throw new ApiException(
            ErrorCode.REPORT_RATE_LIMITED, "At most 20 advanced report requests per minute");
    } catch (DataAccessException e) {
      throw new ApiException(ErrorCode.RESOURCE_BUSY, "Report rate limiter unavailable");
    }
  }
}
