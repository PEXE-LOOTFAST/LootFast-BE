package com.lootfast.backend.admin.report.service;

import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class SystemReportRateLimiter {
  private final StringRedisTemplate redis;
  private static final DefaultRedisScript<Long> LIMIT =
      new DefaultRedisScript<>(
          "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],60) end;"
              + " return n",
          Long.class);

  public SystemReportRateLimiter(StringRedisTemplate redis) {
    this.redis = redis;
  }

  public void requireAllowed(long admin) {
    try {
      Long n = redis.execute(LIMIT, List.of("rate:admin-system-report:" + admin));
      if (n == null)
        throw new ApiException(ErrorCode.RESOURCE_BUSY, "Report rate limiter unavailable");
      if (n > 10)
        throw new ApiException(
            ErrorCode.REPORT_RATE_LIMITED, "At most 10 system reports per minute");
    } catch (DataAccessException e) {
      throw new ApiException(ErrorCode.RESOURCE_BUSY, "Report rate limiter unavailable");
    }
  }
}
