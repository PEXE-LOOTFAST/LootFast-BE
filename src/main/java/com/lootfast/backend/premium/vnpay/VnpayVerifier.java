package com.lootfast.backend.premium.vnpay;

import com.lootfast.backend.premium.checkout.service.VnpayCheckout;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class VnpayVerifier {
  private final VnpayCheckout gateway;
  private static final DateTimeFormatter DATE =
      DateTimeFormatter.ofPattern("uuuuMMddHHmmss").withResolverStyle(ResolverStyle.STRICT);

  public VnpayVerifier(VnpayCheckout gateway) {
    this.gateway = gateway;
  }

  public VnpayNotification verify(Map<String, String[]> parameters, int queryLength) {
    gateway.requireEnabled();
    if (queryLength > 8192 || queryLength < 0 || parameters.size() > 24)
      throw new InvalidVnpayNotification("99");
    var fields = new TreeMap<String, String>();
    String signature = null;
    for (var e : parameters.entrySet()) {
      if (!e.getKey().matches("vnp_[A-Za-z0-9_]{1,40}")
          || e.getValue() == null
          || e.getValue().length != 1
          || e.getValue()[0] == null) throw new InvalidVnpayNotification("99");
      String value = e.getValue()[0];
      if (value.length() > 512 || value.chars().anyMatch(c -> c < 32 || c > 126))
        throw new InvalidVnpayNotification("99");
      if (e.getKey().equals("vnp_SecureHash")) signature = value;
      else if (e.getKey().equals("vnp_SecureHashType")) {
        if (!value.isEmpty() && !value.equalsIgnoreCase("HMACSHA512"))
          throw new InvalidVnpayNotification("97");
      } else if (!value.isEmpty()) fields.put(e.getKey(), value);
    }
    String canonical =
        fields.entrySet().stream()
            .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
            .collect(java.util.stream.Collectors.joining("&"));
    if (signature == null
        || !signature.matches("[0-9a-fA-F]{128}")
        || !gateway.authentic(canonical, signature)) throw new InvalidVnpayNotification("97");
    String merchant = required(fields, "vnp_TmnCode", "[A-Za-z0-9]{8}");
    if (!gateway.merchantCode().equals(merchant)) throw new InvalidVnpayNotification("97");
    String ref = required(fields, "vnp_TxnRef", "[A-Za-z0-9]{1,100}");
    long amount = Long.parseLong(required(fields, "vnp_Amount", "[0-9]{1,12}"));
    String txn =
        Long.toString(Long.parseLong(required(fields, "vnp_TransactionNo", "[0-9]{1,15}")));
    String response = required(fields, "vnp_ResponseCode", "[0-9]{2}"),
        status = required(fields, "vnp_TransactionStatus", "[0-9]{2}");
    String bank = fields.getOrDefault("vnp_BankCode", "");
    if (!bank.isEmpty() && !bank.matches("[A-Za-z0-9]{3,20}"))
      throw new InvalidVnpayNotification("99");
    if (!fields.containsKey("vnp_OrderInfo") || fields.get("vnp_OrderInfo").length() > 255)
      throw new InvalidVnpayNotification("99");
    if (response.equals("00") && status.equals("00") && (txn.equals("0") || bank.isEmpty()))
      throw new InvalidVnpayNotification("99");
    Instant paid = null;
    if (fields.containsKey("vnp_PayDate")) {
      try {
        String date = required(fields, "vnp_PayDate", "[0-9]{14}");
        paid = LocalDateTime.parse(date, DATE).atOffset(ZoneOffset.ofHours(7)).toInstant();
      } catch (DateTimeException e) {
        throw new InvalidVnpayNotification("99");
      }
    }
    try {
      return new VnpayNotification(
          ref,
          amount,
          merchant,
          txn,
          response,
          status,
          bank,
          paid,
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(canonical.getBytes(StandardCharsets.US_ASCII))));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable");
    }
  }

  private static String required(Map<String, String> f, String key, String regex) {
    var v = f.get(key);
    if (v == null || !v.matches(regex)) throw new InvalidVnpayNotification("99");
    return v;
  }

  private static String encode(String v) {
    return URLEncoder.encode(v, StandardCharsets.US_ASCII);
  }
}
