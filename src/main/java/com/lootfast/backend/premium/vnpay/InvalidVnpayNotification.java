package com.lootfast.backend.premium.vnpay;

public class InvalidVnpayNotification extends RuntimeException {
  private final String code;

  public InvalidVnpayNotification(String code) {
    super("Invalid VNPay notification");
    this.code = code;
  }

  public String code() {
    return code;
  }
}
