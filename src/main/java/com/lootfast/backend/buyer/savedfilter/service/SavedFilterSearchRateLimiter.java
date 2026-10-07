package com.lootfast.backend.buyer.savedfilter.service;

import com.lootfast.backend.common.exception.*;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class SavedFilterSearchRateLimiter {
  private final StringRedisTemplate redis;
  private static final DefaultRedisScript<Long> LIMIT =
      new DefaultRedisScript<>(
          "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],60) end;"
              + " return n",
          Long.class);

  public SavedFilterSearchRateLimiter(StringRedisTemplate redis) {
    this.redis = redis;
  }

  public void requireAllowed(long user) {
    try {
      Long n = redis.execute(LIMIT, List.of("rate:buyer-saved-filter-search:" + user));
      if (n == null)
        throw new ApiException(ErrorCode.RESOURCE_BUSY, "Saved filter search limiter unavailable");
      if (n > 30)
        throw new ApiException(
            ErrorCode.SAVED_FILTER_SEARCH_RATE_LIMITED,
            "At most 30 saved filter searches per minute");
    } catch (DataAccessException e) {
      throw new ApiException(ErrorCode.RESOURCE_BUSY, "Saved filter search limiter unavailable");
    }
  }
}
