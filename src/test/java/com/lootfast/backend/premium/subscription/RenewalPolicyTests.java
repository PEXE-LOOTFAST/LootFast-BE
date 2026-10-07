package com.lootfast.backend.premium.subscription;

import static org.junit.jupiter.api.Assertions.*;

import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.premium.subscription.service.RenewalPolicy;
import java.time.*;
import org.junit.jupiter.api.Test;

class RenewalPolicyTests {
  final RenewalPolicy policy = new RenewalPolicy();
  final Instant now = Instant.parse("2030-01-01T00:00:00Z");

  @Test
  void newExpiredAndExactExpiryStartNow() {
    for (Instant end : new Instant[] {null, now.minusSeconds(1), now}) {
      var w = policy.allocate(now, end, 30);
      assertEquals(now, w.startsAt());
      assertEquals(now.plus(Duration.ofDays(30)), w.endsAt());
    }
  }

  @Test
  void activeRenewalStacksAndUsesFixedDayDuration() {
    var end = Instant.parse("2032-02-28T00:00:00Z");
    var w = policy.allocate(now, end, 2);
    assertEquals(end, w.startsAt());
    assertEquals(Instant.parse("2032-03-01T00:00:00Z"), w.endsAt());
  }

  @Test
  void precisionMatchesDatabase() {
    assertEquals(
        now.plusNanos(123456000), policy.allocate(now.plusNanos(123456789), null, 1).startsAt());
  }

  @Test
  void invalidAndOverflowInputsFail() {
    for (int n : new int[] {0, -1, 367})
      assertThrows(ApiException.class, () -> policy.allocate(now, null, n));
    assertThrows(ApiException.class, () -> policy.allocate(null, null, 1));
    assertThrows(ApiException.class, () -> policy.allocate(now, Instant.MAX, 366));
    assertThrows(
        ApiException.class, () -> policy.allocate(Instant.parse("9998-12-31T00:00:00Z"), null, 1));
  }
}
