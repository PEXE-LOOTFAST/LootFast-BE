package com.lootfast.backend.premium.vnpay.query;

import com.lootfast.backend.common.exception.*;
import com.lootfast.backend.premium.checkout.service.VnpayCheckout;
import java.net.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class QuerySettings {
  private final boolean enabled;
  private final URI url;
  private final String ip;
  private final VnpayCheckout gateway;

  public QuerySettings(
      VnpayCheckout gateway,
      @Value("${lootfast.vnpay.query.enabled:false}") boolean enabled,
      @Value(
              "${lootfast.vnpay.query.url:https://sandbox.vnpayment.vn/merchant_webapi/api/transaction}")
          String url,
      @Value("${lootfast.vnpay.query.ip:127.0.0.1}") String ip) {
    this.gateway = gateway;
    this.enabled = enabled;
    this.url = URI.create(url);
    this.ip = ip;
    if (enabled) {
      gateway.requireEnabled();
      if (!url.equals("https://sandbox.vnpayment.vn/merchant_webapi/api/transaction"))
        throw new IllegalArgumentException("QueryDr must use the fixed VNPay sandbox endpoint");
      if (!literalIp(ip))
        throw new IllegalArgumentException("VNPAY_QUERY_IP must be a literal server IP");
    }
  }

  static boolean literalIp(String ip) {
    if (ip == null || ip.length() > 45) return false;
    if (ip.matches("[0-9]+(\\.[0-9]+){3}")) {
      return java.util.Arrays.stream(ip.split("\\."))
          .allMatch(x -> x.length() <= 3 && Integer.parseInt(x) <= 255);
    }
    if (!ip.matches("[0-9a-fA-F:]+") || !ip.contains(":")) return false;
    try {
      return InetAddress.getByName(ip) instanceof Inet6Address;
    } catch (UnknownHostException e) {
      return false;
    }
  }

  public void requireEnabled() {
    gateway.requireEnabled();
    if (!enabled)
      throw new ApiException(ErrorCode.PAYMENT_NOT_CONFIGURED, "VNPay reconciliation is disabled");
  }

  public URI url() {
    return url;
  }

  public String ip() {
    return ip;
  }
}
