package com.lootfast.backend.premium.vnpay.query;

import com.fasterxml.jackson.databind.*;
import com.lootfast.backend.premium.checkout.service.VnpayCheckout;
import com.lootfast.backend.premium.vnpay.VnpayNotification;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class QueryProtocol {
  private final VnpayCheckout gateway;
  private final ObjectMapper json;
  static final DateTimeFormatter DATE =
      DateTimeFormatter.ofPattern("uuuuMMddHHmmss").withResolverStyle(ResolverStyle.STRICT);
  static final List<String> RESPONSE_FIELDS =
      List.of(
          "vnp_ResponseId",
          "vnp_Command",
          "vnp_ResponseCode",
          "vnp_Message",
          "vnp_TmnCode",
          "vnp_TxnRef",
          "vnp_Amount",
          "vnp_BankCode",
          "vnp_PayDate",
          "vnp_TransactionNo",
          "vnp_TransactionType",
          "vnp_TransactionStatus",
          "vnp_OrderInfo",
          "vnp_PromotionCode",
          "vnp_PromotionAmount");

  public QueryProtocol(VnpayCheckout gateway, ObjectMapper json) {
    this.gateway = gateway;
    this.json = json;
  }

  static String date(Instant time) {
    return DATE.format(time.atOffset(ZoneOffset.ofHours(7)));
  }

  public Map<String, String> request(QueryJob job, Instant now, String ip) {
    var p = new LinkedHashMap<String, String>();
    p.put("vnp_RequestId", job.token().toString().replace("-", ""));
    p.put("vnp_Version", "2.1.0");
    p.put("vnp_Command", "querydr");
    p.put("vnp_TmnCode", gateway.merchantCode());
    p.put("vnp_TxnRef", job.reference());
    p.put("vnp_TransactionDate", date(job.transactionDate()));
    p.put("vnp_CreateDate", date(now));
    p.put("vnp_IpAddr", ip);
    p.put("vnp_OrderInfo", "Query Premium " + job.reference());
    p.put("vnp_SecureHash", gateway.sign(String.join("|", p.values())));
    return p;
  }

  public QueryResult response(QueryJob job, byte[] bytes) {
    try {
      if (bytes.length > 16384) return QueryResult.retry("RESPONSE_TOO_LARGE");
      // Reject duplicate JSON keys rather than letting the last value win.
      JsonNode data;
      try (var parser = json.getFactory().createParser(bytes)) {
        parser.enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        data = json.readTree(parser);
        if (parser.nextToken() != null) return QueryResult.retry("INVALID_RESPONSE");
      }
      if (data == null || !data.isObject() || data.size() > 24)
        return QueryResult.retry("INVALID_RESPONSE");
      var p = new HashMap<String, String>();
      var fields = data.properties().iterator();
      while (fields.hasNext()) {
        var e = fields.next();
        if (!e.getValue().isTextual()
            && !(java.util.Set.of("vnp_Amount", "vnp_TransactionNo", "vnp_PromotionAmount")
                    .contains(e.getKey())
                && e.getValue().isIntegralNumber())) return QueryResult.retry("INVALID_RESPONSE");
        String v = e.getValue().asText();
        if (v.length() > 512 || v.contains("|") || v.chars().anyMatch(c -> c < 32))
          return QueryResult.retry("INVALID_RESPONSE");
        p.put(e.getKey(), v);
      }
      String canonical =
          RESPONSE_FIELDS.stream()
              .map(k -> p.getOrDefault(k, ""))
              .collect(java.util.stream.Collectors.joining("|"));
      String hash = p.getOrDefault("vnp_SecureHash", "");
      if (!hash.matches("[a-fA-F0-9]{128}") || !gateway.authentic(canonical, hash))
        return QueryResult.retry("INVALID_SIGNATURE");
      String code = p.getOrDefault("vnp_ResponseCode", "");
      String rid = p.getOrDefault("vnp_ResponseId", "");
      if (!code.matches("[0-9]{2}")
          || !rid.matches("[A-Za-z0-9]{1,32}")
          || !(p.getOrDefault("vnp_Command", "").isEmpty()
              || p.get("vnp_Command").equals("querydr")))
        return QueryResult.retry("INVALID_RESPONSE");
      if (!gateway.merchantCode().equals(p.get("vnp_TmnCode"))
          || !job.reference().equals(p.get("vnp_TxnRef")))
        return QueryResult.retry("IDENTITY_MISMATCH");
      if (!code.equals("00")) return new QueryResult("PROVIDER_ERROR", code, rid, null);
      if (!p.getOrDefault("vnp_TransactionType", "").equals("01"))
        return new QueryResult("NON_PAYMENT", code, rid, null);
      String amount = p.getOrDefault("vnp_Amount", ""),
          txn = p.getOrDefault("vnp_TransactionNo", ""),
          status = p.getOrDefault("vnp_TransactionStatus", ""),
          bank = p.getOrDefault("vnp_BankCode", "");
      if (!amount.matches("[0-9]{1,12}")
          || !txn.matches("[0-9]{1,15}")
          || !status.matches("[0-9]{2}")
          || (!bank.isEmpty() && !bank.matches("[A-Za-z0-9]{3,20}"))
          || p.getOrDefault("vnp_OrderInfo", "").isEmpty()
          || p.get("vnp_OrderInfo").length() > 255) return QueryResult.retry("INVALID_RESPONSE");
      txn = Long.toString(Long.parseLong(txn));
      if (status.equals("00") && (txn.equals("0") || bank.isEmpty()))
        return QueryResult.retry("INVALID_RESPONSE");
      Instant paid = null;
      var date = p.getOrDefault("vnp_PayDate", "");
      if (!date.isEmpty()) {
        if (!date.matches("[0-9]{14}")) return QueryResult.retry("INVALID_RESPONSE");
        paid = LocalDateTime.parse(date, DATE).atOffset(ZoneOffset.ofHours(7)).toInstant();
      }
      String fingerprint =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(("QUERYDR|" + canonical).getBytes(StandardCharsets.UTF_8)));
      return new QueryResult(
          "VERIFIED",
          code,
          rid,
          new VnpayNotification(
              job.reference(),
              Long.parseLong(amount),
              gateway.merchantCode(),
              txn,
              code,
              status,
              bank,
              paid,
              fingerprint));
    } catch (java.io.IOException
        | java.security.GeneralSecurityException
        | IllegalArgumentException
        | DateTimeException e) {
      return QueryResult.retry("INVALID_RESPONSE");
    }
  }
}
