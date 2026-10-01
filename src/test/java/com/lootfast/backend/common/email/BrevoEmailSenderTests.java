package com.lootfast.backend.common.email;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class BrevoEmailSenderTests {

    private MockRestServiceServer server;
    private BrevoEmailSender sender;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        BrevoEmailProperties properties = new BrevoEmailProperties(
                "https://api.brevo.com/v3",
                "test-api-key",
                "lootfast.app@gmail.com",
                "LootFast",
                Duration.ofSeconds(3),
                Duration.ofSeconds(10)
        );
        sender = new BrevoEmailSender(
                builder.baseUrl(properties.baseUrl())
                        .defaultHeader("api-key", properties.apiKey())
                        .build(),
                properties
        );
    }

    @Test
    void sendsSignupOtpUsingBrevoTransactionalApi() {
        server.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", "test-api-key"))
                .andExpect(jsonPath("$.sender.name").value("LootFast"))
                .andExpect(jsonPath("$.sender.email").value("lootfast.app@gmail.com"))
                .andExpect(jsonPath("$.to[0].email").value("user@example.com"))
                .andExpect(jsonPath("$.subject").value("LootFast — Mã xác thực đăng ký"))
                .andExpect(jsonPath("$.htmlContent").value(org.hamcrest.Matchers.containsString("123456")))
                .andExpect(jsonPath("$.headers.idempotencyKey").value(
                        org.hamcrest.Matchers.matchesPattern(
                                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
                        )
                ))
                .andRespond(withSuccess(
                        "{\"messageId\":\"<test-message@brevo>\"}",
                        MediaType.APPLICATION_JSON
                ));

        sender.sendSignupOtp("user@example.com", "123456");

        server.verify();
    }

    @Test
    void containsProviderFailureSoAsyncCallerDoesNotReceiveTransportDetails() {
        server.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andRespond(withBadRequest());

        assertDoesNotThrow(() -> sender.sendPasswordResetOtp("user@example.com", "123456"));

        server.verify();
    }

    @Test
    void sendsMatchingDealEmailWithStableNotificationKeyAndEscapedContent() {
        UUID notificationId = UUID.randomUUID();
        server.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.to[0].email").value("buyer@example.com"))
                .andExpect(jsonPath("$.subject").value("LootFast — Deal phù hợp dành cho bạn"))
                .andExpect(jsonPath("$.headers.idempotencyKey").value(notificationId.toString()))
                .andExpect(jsonPath("$.htmlContent").value(
                        org.hamcrest.Matchers.containsString("Bread &amp; Soup")
                ))
                .andExpect(jsonPath("$.htmlContent").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<script>"))
                ))
                .andRespond(withSuccess(
                        "{\"messageId\":\"<matching-deal@brevo>\"}",
                        MediaType.APPLICATION_JSON
                ));

        sender.sendMatchingDeal(
                "buyer@example.com",
                notificationId,
                "Bread & Soup",
                "<script>Store</script>",
                BigDecimal.valueOf(100_000),
                BigDecimal.valueOf(40_000)
        );

        server.verify();
    }
    @Test
    void sendsOrderEmailWithEscapedContentAndStableKey() {
        UUID id = UUID.randomUUID();
        server.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andExpect(jsonPath("$.headers.idempotencyKey").value(id.toString()))
                .andExpect(jsonPath("$.htmlContent").value(
                        org.hamcrest.Matchers.containsString("&lt;script&gt;")))
                .andRespond(withSuccess("{\"messageId\":\"order-message\"}", MediaType.APPLICATION_JSON));
        sender.sendOrderNotification("buyer@example.com", id, "Order update", "<script>Meal</script>");
        server.verify();
    }
}
