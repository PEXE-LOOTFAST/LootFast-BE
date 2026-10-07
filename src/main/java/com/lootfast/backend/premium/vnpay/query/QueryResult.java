package com.lootfast.backend.premium.vnpay.query;

import com.lootfast.backend.premium.vnpay.VnpayNotification;

public record QueryResult(
    String result, String code, String responseId, VnpayNotification notification) {
  public static QueryResult retry(String result) {
    return new QueryResult(result, null, null, null);
  }

  @Override
  public String toString() {
    return "QueryResult[result=" + result + "]";
  }
}
