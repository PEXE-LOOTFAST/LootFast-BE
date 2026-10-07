package com.lootfast.backend.premium.checkout;

import static org.junit.jupiter.api.Assertions.*;

import com.lootfast.backend.common.exception.*;
import com.lootfast.backend.premium.checkout.service.VnpayCheckout;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class VnpayCheckoutTests {
  static final String PAY = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
      RET = "https://example.test/premium/result";
  static final Instant NOW = Instant.parse("2030-01-02T00:00:00Z");

  VnpayCheckout gateway() {
    return new VnpayCheckout(true, "TEST0001", "test-only-secret", PAY, RET);
  }

  @Test
  void independentCanonicalSignatureVector() {
    assertEquals(
        PAY
            + "?vnp_Amount=4900000&vnp_Command=pay&vnp_CreateDate=20300102070000&vnp_CurrCode=VND&vnp_ExpireDate=20300102071500&vnp_IpAddr=127.0.0.1&vnp_Locale=vn&vnp_OrderInfo=LootFast+Premium+A123&vnp_OrderType=other&vnp_ReturnUrl=https%3A%2F%2Fexample.test%2Fpremium%2Fresult&vnp_TmnCode=TEST0001&vnp_TxnRef=A123&vnp_Version=2.1.0&vnp_SecureHash=4b599fc0e0f41c90532c8ccfbf6e59e1dc4651c93803a1ec7359a0dff09b4ba631b100842afc15826c00a216b0ec886818f5fbac43685a2f6c489b62ed249e3a",
        gateway().url("A123", 49000, NOW, NOW.plusSeconds(900), "127.0.0.1"));
  }

  @Test
  void disabledNeedsNoKeysAndCannotCheckout() {
    var g = new VnpayCheckout(false, "", "", "", "");
    assertEquals(
        ErrorCode.PAYMENT_NOT_CONFIGURED,
        assertThrows(ApiException.class, g::requireEnabled).getErrorCode());
  }

  @Test
  void enabledFailsFastForMissingCredentialsOrProductionEndpoint() {
    assertThrows(IllegalArgumentException.class, () -> new VnpayCheckout(true, "", "", PAY, RET));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new VnpayCheckout(true, "TEST0001", "secret", "https://pay.vnpay.vn/vpcpay.html", RET));
  }

  @Test
  void returnUrlCannotBeCallerControlledOrUnsafe() {
    for (String u :
        new String[] {
          "",
          "http://example.test/return",
          "https://user:pass@example.test/return",
          "https://example.test/return?next=x",
          "https://example.test/return#x"
        })
      assertThrows(
          IllegalArgumentException.class,
          () -> new VnpayCheckout(true, "TEST0001", "secret", PAY, u));
  }

  @Test
  void rejectsOverflowAndDnsOrInvalidIp() {
    for (long amount : new long[] {0, 999, 1000000000L, Long.MAX_VALUE})
      assertThrows(
          ApiException.class,
          () -> gateway().url("A123", amount, NOW, NOW.plusSeconds(900), "127.0.0.1"));
    for (String ip : new String[] {"host.example", "256.0.0.1", "1.2.3", "::1%eth0"})
      assertThrows(
          ApiException.class, () -> gateway().url("A123", 49000, NOW, NOW.plusSeconds(900), ip));
    assertTrue(
        gateway()
            .url("A123", 49000, NOW, NOW.plusSeconds(900), "::1")
            .contains("vnp_IpAddr=%3A%3A1"));
  }

  @Test
  void invalidReferenceOrDeadlineRejected() {
    assertThrows(
        ApiException.class,
        () -> gateway().url("ref&amount=1", 49000, NOW, NOW.plusSeconds(900), "127.0.0.1"));
    assertThrows(ApiException.class, () -> gateway().url("A123", 49000, NOW, NOW, "127.0.0.1"));
  }
}
