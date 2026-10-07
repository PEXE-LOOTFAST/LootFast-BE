package com.lootfast.backend.premium.vnpay;

import static org.junit.jupiter.api.Assertions.*;

import com.lootfast.backend.premium.checkout.service.VnpayCheckout;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class VnpayBoundaryTests {
  final VnpayVerifier verifier =
      new VnpayVerifier(
          new VnpayCheckout(
              true,
              "TEST0001",
              "test-only-secret",
              "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
              "https://backend.test/api/v1/payments/vnpay/return"));

  Map<String, String> fields() {
    var fields = new TreeMap<String, String>();
    fields.put("vnp_TmnCode", "TEST0001");
    fields.put("vnp_TxnRef", "A123");
    fields.put("vnp_Amount", "4900000");
    fields.put("vnp_TransactionNo", "000123");
    fields.put("vnp_ResponseCode", "00");
    fields.put("vnp_TransactionStatus", "00");
    fields.put("vnp_BankCode", "NCB");
    fields.put("vnp_OrderInfo", "Premium + buyer & merchant");
    fields.put("vnp_PayDate", "20300102070000");
    return fields;
  }

  Map<String, String[]> signed(Map<String, String> fields) throws Exception {
    var entries = new TreeMap<>(fields);
    String canonical =
        entries.entrySet().stream()
            .filter(e -> !e.getValue().isEmpty())
            .map(
                e ->
                    URLEncoder.encode(e.getKey(), StandardCharsets.US_ASCII)
                        + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.US_ASCII))
            .collect(java.util.stream.Collectors.joining("&"));
    var mac = Mac.getInstance("HmacSHA512");
    mac.init(
        new SecretKeySpec("test-only-secret".getBytes(StandardCharsets.US_ASCII), "HmacSHA512"));
    var result = new HashMap<String, String[]>();
    entries.forEach((key, value) -> result.put(key, new String[] {value}));
    result.put(
        "vnp_SecureHash",
        new String[] {
          HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.US_ASCII)))
        });
    return result;
  }

  @Test
  void encodesSpecialCharactersNormalizesIdentityAndIgnoresHashCase() throws Exception {
    var data = signed(fields());
    var original = verifier.verify(data, 1000);
    data.get("vnp_SecureHash")[0] = data.get("vnp_SecureHash")[0].toUpperCase(Locale.ROOT);
    data.put("vnp_SecureHashType", new String[] {"HMACSHA512"});
    assertEquals(original, verifier.verify(data, 1000));
    assertEquals("123", original.transactionNo());
    assertEquals(Instant.parse("2030-01-02T00:00:00Z"), original.paidAt());
  }

  @Test
  void rejectsSignedInvalidDatesMissingBankAndOversizedAmount() throws Exception {
    for (var entry :
        List.of(
            Map.entry("vnp_PayDate", "20300230070000"),
            Map.entry("vnp_BankCode", ""),
            Map.entry("vnp_TransactionNo", "0"),
            Map.entry("vnp_Amount", "9999999999999"))) {
      var fields = fields();
      fields.put(entry.getKey(), entry.getValue());
      var data = signed(fields);
      assertEquals(
          "99",
          assertThrows(InvalidVnpayNotification.class, () -> verifier.verify(data, 1000)).code());
    }
  }

  @Test
  void rejectsLargeRequestsDuplicateFieldsAndModifiedSignedFields() throws Exception {
    var data = signed(fields());
    assertThrows(InvalidVnpayNotification.class, () -> verifier.verify(data, 8193));
    data.put("vnp_Amount", new String[] {"4900000", "1"});
    assertThrows(InvalidVnpayNotification.class, () -> verifier.verify(data, 1000));
    data.put("vnp_Amount", new String[] {"1"});
    assertEquals(
        "97",
        assertThrows(InvalidVnpayNotification.class, () -> verifier.verify(data, 1000)).code());
  }

  @Test
  void fixedFrontendDestinationDoesNotLeakProviderData() {
    var order = UUID.randomUUID();
    var destination = new VnpayReturnDestination("https://frontend.test/payment-result");
    assertEquals(
        "https://frontend.test/payment-result?orderId=" + order,
        destination.result(order, true).toString());
    assertEquals(
        "https://frontend.test/payment-result?error=invalid_callback",
        destination.result(null, false).toString());
    assertNull(new VnpayReturnDestination("").result(order, true));
    assertNotNull(new VnpayReturnDestination("http://localhost:3000/result").result(order, true));
    for (var url :
        List.of(
            "http://external.test/result",
            "//external.test/result",
            "https://user:password@external.test/result",
            "https://frontend.test/result?next=https://external.test",
            "https://frontend.test/result#next")) {
      assertThrows(IllegalArgumentException.class, () -> new VnpayReturnDestination(url));
    }
  }
}
