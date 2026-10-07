package com.lootfast.backend.premium.vnpay.query;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.premium.checkout.service.VnpayCheckout;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class QueryProtocolTests {
  static final Instant NOW = Instant.parse("2030-01-02T00:20:00Z");
  final VnpayCheckout gateway =
      new VnpayCheckout(
          true,
          "TEST0001",
          "test-only-secret",
          "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
          "https://backend.test/return");
  final ObjectMapper json = new ObjectMapper();
  final QueryProtocol protocol = new QueryProtocol(gateway, json);
  final QueryJob job =
      new QueryJob(
          UUID.randomUUID(),
          UUID.fromString("00000000-0000-0000-0000-000000000001"),
          "A123",
          NOW.minusSeconds(1200),
          1);

  Map<String, String> response() {
    var p = new LinkedHashMap<String, String>();
    p.put("vnp_ResponseId", "R123");
    p.put("vnp_Command", "querydr");
    p.put("vnp_ResponseCode", "00");
    p.put("vnp_Message", "Tra cứu thành công");
    p.put("vnp_TmnCode", "TEST0001");
    p.put("vnp_TxnRef", "A123");
    p.put("vnp_Amount", "4900000");
    p.put("vnp_BankCode", "NCB");
    p.put("vnp_PayDate", "20300102071000");
    p.put("vnp_TransactionNo", "000123");
    p.put("vnp_TransactionType", "01");
    p.put("vnp_TransactionStatus", "00");
    p.put("vnp_OrderInfo", "Query Premium A123");
    p.put("vnp_PromotionCode", "");
    p.put("vnp_PromotionAmount", "");
    return p;
  }

  byte[] signed(Map<String, String> p) throws Exception {
    var fields = new LinkedHashMap<>(p);
    fields.remove("vnp_SecureHash");
    var mac = Mac.getInstance("HmacSHA512");
    mac.init(new SecretKeySpec("test-only-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
    fields.put(
        "vnp_SecureHash",
        HexFormat.of()
            .formatHex(
                mac.doFinal(String.join("|", fields.values()).getBytes(StandardCharsets.UTF_8))));
    return json.writeValueAsBytes(fields);
  }

  @Test
  void requestUsesOriginalCheckoutDateAndNewRequestDate() {
    var p = protocol.request(job, NOW, "127.0.0.1");
    assertEquals("20300102070000", p.get("vnp_TransactionDate"));
    assertEquals("20300102072000", p.get("vnp_CreateDate"));
    assertEquals("00000000000000000000000000000001", p.get("vnp_RequestId"));
    assertEquals(
        "fbefb8fd9c4ec8a975155717679b99704461db446cfb554394f0753474380fac1d16f65d2da4becca3e4fc6e1b1284e5bd6a0198a7cda46998f0dbc28db72e8f",
        p.get("vnp_SecureHash"));
  }

  @Test
  void responseVerifiesUtf8AndNormalizesProviderIdentity() throws Exception {
    var result = protocol.response(job, signed(response()));
    assertEquals("VERIFIED", result.result());
    assertTrue(result.notification().successful());
    assertEquals("123", result.notification().transactionNo());
  }

  @Test
  void modifiedSignatureCannotGrant() throws Exception {
    var data = new String(signed(response()), StandardCharsets.UTF_8).replace("4900000", "1");
    assertEquals(
        "INVALID_SIGNATURE",
        protocol.response(job, data.getBytes(StandardCharsets.UTF_8)).result());
  }

  @Test
  void errorsAndRefundResultsNeverBecomePayments() throws Exception {
    var data = response();
    data.put("vnp_ResponseCode", "91");
    var notFound = protocol.response(job, signed(data));
    assertEquals("PROVIDER_ERROR", notFound.result());
    assertNull(notFound.notification());
    data = response();
    data.put("vnp_TransactionType", "02");
    assertEquals("NON_PAYMENT", protocol.response(job, signed(data)).result());
  }

  @Test
  void signedOtherReferenceWrongMerchantOrDateAreRejected() throws Exception {
    for (var entry :
        List.of(
            Map.entry("vnp_TxnRef", "B456"),
            Map.entry("vnp_TmnCode", "OTHER001"),
            Map.entry("vnp_PayDate", "20300230070000"))) {
      var data = response();
      data.put(entry.getKey(), entry.getValue());
      assertNull(protocol.response(job, signed(data)).notification());
    }
  }

  @Test
  void duplicateJsonKeysAndLargeBodiesAreRejected() throws Exception {
    var raw =
        new String(signed(response()), StandardCharsets.UTF_8)
            .replace("{", "{\"vnp_Amount\":\"1\",");
    assertEquals(
        "INVALID_RESPONSE", protocol.response(job, raw.getBytes(StandardCharsets.UTF_8)).result());
    assertEquals("RESPONSE_TOO_LARGE", protocol.response(job, new byte[16385]).result());
  }

  @Test
  void pendingAndFailedTransactionRemainDistinctFromSuccessfulQuery() throws Exception {
    for (var status : List.of("01", "02", "07")) {
      var data = response();
      data.put("vnp_TransactionStatus", status);
      var result = protocol.response(job, signed(data));
      assertEquals("VERIFIED", result.result());
      assertFalse(result.notification().successful());
    }
  }

  @Test
  void settingsAllowOnlyFixedSandboxEndpointAndLiteralIp() {
    assertNotNull(
        new QuerySettings(
            gateway,
            true,
            "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction",
            "127.0.0.1"));
    for (var ip : List.of("hostname.test", "999.1.1.1", "1.2.3", "::1%eth0"))
      assertFalse(QuerySettings.literalIp(ip));
    assertTrue(QuerySettings.literalIp("::1"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new QuerySettings(gateway, true, "https://evil.test/query", "127.0.0.1"));
    assertThrows(
        ApiException.class,
        () ->
            new QuerySettings(
                new VnpayCheckout(false, "", "", "", ""),
                true,
                "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction",
                "127.0.0.1"));
    assertThrows(
        ApiException.class,
        () ->
            new QuerySettings(
                    gateway,
                    false,
                    "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction",
                    "127.0.0.1")
                .requireEnabled());
  }

  @Test
  void transportSubscriberCancelsOversizedResponse() {
    var subscriber = new QueryClient.LimitedBody();
    var subscription = mock(java.util.concurrent.Flow.Subscription.class);
    subscriber.onSubscribe(subscription);
    subscriber.onNext(List.of(ByteBuffer.wrap(new byte[16385])));
    verify(subscription).cancel();
    assertThrows(
        CompletionException.class, () -> subscriber.getBody().toCompletableFuture().join());
  }
}
