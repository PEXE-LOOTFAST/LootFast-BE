package com.lootfast.backend.premium.vnpay;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VnpayAck(
    @JsonProperty("RspCode") String code, @JsonProperty("Message") String message) {
  public static VnpayAck of(String code) {
    return new VnpayAck(
        code,
        switch (code) {
          case "00" -> "Confirm Success";
          case "02" -> "Order already confirmed";
          case "01" -> "Order not found";
          case "04" -> "Invalid amount";
          case "97" -> "Invalid checksum";
          default -> "Unable to process notification";
        });
  }
}
