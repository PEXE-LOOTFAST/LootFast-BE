package com.lootfast.backend.premium.subscription.service;

import com.lootfast.backend.common.exception.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

@Component
public class RenewalPolicy {
  public record Window(Instant startsAt, Instant endsAt) {}

  public Window allocate(Instant now, Instant paidEndsAt, int days) {
    if (now == null || days < 1 || days > 366)
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "Duration must be 1 to 366 days");
    Instant start =
        (paidEndsAt != null && paidEndsAt.isAfter(now) ? paidEndsAt : now)
            .truncatedTo(ChronoUnit.MICROS);
    try {
      var end = start.plus(Duration.ofDays(days));
      if (start.isBefore(Instant.parse("0001-01-01T00:00:00Z"))
          || !end.isBefore(Instant.parse("9999-01-01T00:00:00Z")))
        throw new ApiException(
            ErrorCode.VALIDATION_FAILED, "Subscription dates exceed supported range");
      return new Window(start, end);
    } catch (DateTimeException | ArithmeticException e) {
      throw new ApiException(
          ErrorCode.VALIDATION_FAILED, "Subscription dates exceed supported range");
    }
  }
}
