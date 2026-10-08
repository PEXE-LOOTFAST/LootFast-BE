package com.lootfast.backend.premium.vnpay;

import java.time.Instant;

public record VnpayNotification(
    String reference,
    long amount,
    String merchant,
    String transactionNo,
    String responseCode,
    String transactionStatus,
    String bankCode,
    Instant paidAt,
    String fingerprint) {
  public boolean successful() {
    return responseCode.equals("00") && transactionStatus.equals("00");
  }

  @Override
  public String toString() {
    return "VnpayNotification[REDACTED]";
  }
}
