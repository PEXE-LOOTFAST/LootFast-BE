package com.lootfast.backend.premium.checkout.service;

import com.lootfast.backend.common.exception.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class VnpayCheckout {
  private final boolean enabled;
  private final String tmn, secret, paymentUrl, returnUrl;
  private static final DateTimeFormatter DATE =
      DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

  public VnpayCheckout(
      @Value("${lootfast.vnpay.enabled:false}") boolean enabled,
      @Value("${lootfast.vnpay.tmn-code:}") String tmn,
      @Value("${lootfast.vnpay.hash-secret:}") String secret,
      @Value("${lootfast.vnpay.payment-url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}")
          String paymentUrl,
      @Value("${lootfast.vnpay.return-url:}") String returnUrl) {
    this.enabled = enabled;
    this.tmn = tmn;
    this.secret = secret;
    this.paymentUrl = paymentUrl;
    this.returnUrl = returnUrl;
    if (enabled) {
      if (!tmn.matches("[A-Za-z0-9]{8}") || secret.isBlank())
        throw new IllegalArgumentException("Missing or invalid VNPAY_TMN_CODE/VNPAY_HASH_SECRET");
      if (!paymentUrl.equals("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html"))
        throw new IllegalArgumentException(
            "VNPAY_PAYMENT_URL must use sandbox until IPN fulfillment is implemented");
      try {
        var uri = URI.create(returnUrl);
        if (!"https".equals(uri.getScheme())
            || uri.getHost() == null
            || uri.getUserInfo() != null
            || uri.getQuery() != null
            || uri.getFragment() != null
            || returnUrl.length() > 255
            || returnUrl.chars().anyMatch(c -> c > 127)) throw new IllegalArgumentException();
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException(
            "VNPAY_RETURN_URL must be a fixed HTTPS URL without query or fragment");
      }
    }
  }

  public void requireEnabled() {
    if (!enabled)
      throw new ApiException(ErrorCode.PAYMENT_NOT_CONFIGURED, "VNPay checkout is disabled");
  }

  public String url(String reference, long amount, Instant created, Instant expires, String ip) {
    requireEnabled();
    if (reference == null
        || !reference.matches("[A-Za-z0-9]{1,100}")
        || amount < 1000
        || amount > 999999999L
        || created == null
        || expires == null
        || !expires.isAfter(created)
        || !validIp(ip)) throw new ApiException(ErrorCode.VALIDATION_FAILED);
    var p = new TreeMap<String, String>();
    p.put("vnp_Version", "2.1.0");
    p.put("vnp_Command", "pay");
    p.put("vnp_TmnCode", tmn);
    p.put("vnp_Amount", Long.toString(Math.multiplyExact(amount, 100L)));
    p.put("vnp_CurrCode", "VND");
    p.put("vnp_TxnRef", reference);
    p.put("vnp_OrderInfo", "LootFast Premium " + reference);
    p.put("vnp_OrderType", "other");
    p.put("vnp_Locale", "vn");
    p.put("vnp_IpAddr", ip);
    p.put("vnp_ReturnUrl", returnUrl);
    p.put("vnp_CreateDate", DATE.format(created));
    p.put("vnp_ExpireDate", DATE.format(expires));
    var query =
        p.entrySet().stream()
            .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
            .collect(java.util.stream.Collectors.joining("&"));
    try {
      var mac = Mac.getInstance("HmacSHA512");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
      return paymentUrl
          + "?"
          + query
          + "&vnp_SecureHash="
          + HexFormat.of().formatHex(mac.doFinal(query.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.GeneralSecurityException e) {
      throw new IllegalStateException("Cannot sign VNPay request");
    }
  }

  private static String encode(String s) {
    return URLEncoder.encode(s, StandardCharsets.US_ASCII);
  }

  private static boolean validIp(String s) {
    if (s == null || s.length() > 45) return false;
    if (s.contains(":")) {
      if (!s.matches("[0-9a-fA-F:.]+")) return false;
      try {
        return InetAddress.getByName(s) instanceof Inet6Address;
      } catch (UnknownHostException e) {
        return false;
      }
    }
    if (!s.matches("[0-9]+(\\.[0-9]+){3}")) return false;
    try {
      for (var part : s.split("\\."))
        if (part.length() > 3 || Integer.parseInt(part) > 255) return false;
      return true;
    } catch (NumberFormatException e) {
      return false;
    }
  }
}
