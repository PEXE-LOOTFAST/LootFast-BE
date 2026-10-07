package com.lootfast.backend.common.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "email.provider", havingValue = "brevo", matchIfMissing = true)
class BrevoEmailSender implements EmailSender, QueuedEmailSender {

    private static final Logger log = LoggerFactory.getLogger(BrevoEmailSender.class);
    private static final int MAX_ATTEMPTS = 2;
    private static final long RETRY_DELAY_MILLIS = 250;

    private final RestClient restClient;
    private final BrevoEmailProperties properties;

    BrevoEmailSender(RestClient brevoRestClient, BrevoEmailProperties properties) {
        this.restClient = brevoRestClient;
        this.properties = properties;
    }

    @Override
    @Async("emailExecutor")
    public void sendSignupOtp(String to, String otp) {
        sendOtp(
                to,
                otp,
                "signup",
                "LootFast — Mã xác thực đăng ký",
                "Xác thực tài khoản LootFast"
        );
    }

    @Override
    @Async("emailExecutor")
    public void sendPasswordResetOtp(String to, String otp) {
        sendOtp(
                to,
                otp,
                "password-reset",
                "LootFast — Mã đặt lại mật khẩu",
                "Đặt lại mật khẩu LootFast"
        );
    }

    @Override
    @Async("emailExecutor")
    public void sendMatchingDeal(
            String to,
            UUID notificationId,
            String dealName,
            String storeName,
            BigDecimal originalPrice,
            BigDecimal discountedPrice
    ) {
        String safeDealName = HtmlUtils.htmlEscape(dealName);
        String safeStoreName = HtmlUtils.htmlEscape(storeName);
        send(
                new BrevoEmailRequest(
                        new Sender(properties.fromName(), properties.fromEmail()),
                        List.of(new Recipient(to)),
                        "LootFast — Deal phù hợp dành cho bạn",
                        buildMatchingDealBody(
                                safeDealName,
                                safeStoreName,
                                originalPrice,
                                discountedPrice
                        ),
                        Map.of("idempotencyKey", notificationId.toString()),
                        List.of("notification", "matching-deal")
                ),
                "matching-deal"
        );
    }

    @Override
    @Async("emailExecutor")
    public void sendOrderNotification(String to, UUID notificationId, String title, String message) {
        send(new BrevoEmailRequest(
                new Sender(properties.fromName(), properties.fromEmail()), List.of(new Recipient(to)),
                "LootFast — " + title,
                "<h2>" + HtmlUtils.htmlEscape(title) + "</h2><p>"
                        + HtmlUtils.htmlEscape(message) + "</p><p>Mở LootFast để xem đơn hàng.</p>",
                Map.of("idempotencyKey", notificationId.toString()), List.of("notification", "order")),
                "order-notification");
    }

    @Override
    public EmailDeliveryResult sendQueuedEmail(String to, UUID idempotencyKey, String subject, String htmlBody) {
        try {
            var response = restClient.post().uri("/smtp/email").contentType(MediaType.APPLICATION_JSON)
                    .body(new BrevoEmailRequest(new Sender(properties.fromName(), properties.fromEmail()),
                            List.of(new Recipient(to)), subject, htmlBody,
                            Map.of("idempotencyKey", idempotencyKey.toString()), List.of("notification")))
                    .retrieve().body(BrevoEmailResponse.class);
            if (response == null || response.messageId() == null || response.messageId().isBlank()) {
                return EmailDeliveryResult.retry("MISSING_MESSAGE_ID", java.time.Duration.ZERO);
            }
            return EmailDeliveryResult.accepted();
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            // Brevo reports an already accepted idempotency key as a duplicate error.
            // Inspect only bounded structured fields; never log the response body.
            if (status == 400 || status == 409) {
                String body = exception.getResponseBodyAsString();
                if (body.length() <= 65536) {
                    try {
                        var error = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
                        if (error != null && "duplicate_parameter".equals(error.path("code").asText())
                                && error.path("message").asText().toLowerCase(java.util.Locale.ROOT).contains("idempotency")) {
                            return EmailDeliveryResult.accepted();
                        }
                    } catch (java.io.IOException ignored) { /* Continue classifying by HTTP status. */ }
                }
            }
            if (status == 429 || status == 408 || status >= 500) {
                var delay = java.time.Duration.ZERO;
                if (exception.getResponseHeaders() != null) {
                    String value = exception.getResponseHeaders().getFirst("Retry-After");
                    if (value != null) {
                        try { delay = java.time.Duration.ofSeconds(Math.max(0, Math.min(21600, Long.parseLong(value)))); }
                        catch (NumberFormatException ignored) { /* Fallback to outbox backoff. */ }
                    }
                }
                return EmailDeliveryResult.retry("HTTP_" + status, delay);
            }
            return EmailDeliveryResult.failed("HTTP_" + status);
        } catch (ResourceAccessException exception) {
            return EmailDeliveryResult.retry("TRANSPORT_ERROR", java.time.Duration.ZERO);
        } catch (RestClientException exception) {
            return EmailDeliveryResult.retry("INVALID_PROVIDER_RESPONSE", java.time.Duration.ZERO);
        }
    }

    private void sendOtp(String to, String otp, String purpose, String subject, String heading) {
        BrevoEmailRequest request = new BrevoEmailRequest(
                new Sender(properties.fromName(), properties.fromEmail()),
                List.of(new Recipient(to)),
                subject,
                buildBody(otp, heading),
                Map.of("idempotencyKey", idempotencyKey(purpose, to, otp)),
                List.of("otp", purpose)
        );

        send(request, "OTP");
    }

    private void send(BrevoEmailRequest request, String logCategory) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                BrevoEmailResponse response = restClient.post()
                        .uri("/smtp/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(BrevoEmailResponse.class);

                if (response == null || response.messageId() == null || response.messageId().isBlank()) {
                    throw new RestClientException("Brevo returned no messageId");
                }

                log.info("{} email accepted by Brevo, messageId={}",
                        logCategory, sanitizeForLog(response.messageId()));
                return;
            } catch (RestClientResponseException exception) {
                if (attempt < MAX_ATTEMPTS && exception.getStatusCode().is5xxServerError()) {
                    if (!backoffBeforeRetry(attempt)) {
                        return;
                    }
                    continue;
                }
                log.error(
                        "Brevo rejected {} email with HTTP status {}",
                        logCategory,
                        exception.getStatusCode().value()
                );
                return;
            } catch (ResourceAccessException exception) {
                if (attempt < MAX_ATTEMPTS) {
                    if (!backoffBeforeRetry(attempt)) {
                        return;
                    }
                    continue;
                }
                log.error("Brevo {} request failed after {} attempts",
                        logCategory, attempt, exception);
                return;
            } catch (RestClientException exception) {
                log.error("Brevo {} request failed", logCategory, exception);
                return;
            }
        }
    }

    private boolean backoffBeforeRetry(int attempt) {
        try {
            Thread.sleep(RETRY_DELAY_MILLIS * attempt);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Brevo OTP retry interrupted");
            return false;
        }
    }

    private String idempotencyKey(String purpose, String to, String otp) {
        byte[] source = ("lootfast:" + purpose + ':' + to + ':' + otp)
                .getBytes(StandardCharsets.UTF_8);
        return UUID.nameUUIDFromBytes(source).toString();
    }

    private String sanitizeForLog(String value) {
        return value.replace('\r', '_').replace('\n', '_');
    }

    private String buildBody(String otp, String heading) {
        return """
                <div style="font-family:Arial,sans-serif;max-width:480px;margin:auto">
                  <h2>%s</h2>
                  <p>Mã xác thực của bạn:</p>
                  <h1 style="letter-spacing:8px;color:#e74c3c">%s</h1>
                  <p>Mã có hiệu lực trong <strong>5 phút</strong>.</p>
                  <p style="color:#888;font-size:12px">Nếu bạn không yêu cầu điều này, hãy bỏ qua email này.</p>
                </div>
                """.formatted(heading, otp);
    }

    private String buildMatchingDealBody(
            String dealName,
            String storeName,
            BigDecimal originalPrice,
            BigDecimal discountedPrice
    ) {
        NumberFormat money = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN"));
        return """
                <div style="font-family:Arial,sans-serif;max-width:520px;margin:auto">
                  <h2>Deal phù hợp dành cho bạn</h2>
                  <p><strong>%s</strong> tại <strong>%s</strong> vừa được đăng trên LootFast.</p>
                  <p><span style="text-decoration:line-through;color:#888">%s ₫</span>
                     <strong style="color:#e74c3c;font-size:20px">%s ₫</strong></p>
                  <p>Mở LootFast để xem thời gian nhận món và số lượng còn lại.</p>
                </div>
                """.formatted(
                dealName,
                storeName,
                money.format(originalPrice),
                money.format(discountedPrice)
        );
    }

    private record Sender(String name, String email) {
    }

    private record Recipient(String email) {
    }

    private record BrevoEmailRequest(
            Sender sender,
            List<Recipient> to,
            String subject,
            String htmlContent,
            Map<String, String> headers,
            List<String> tags
    ) {
    }

    private record BrevoEmailResponse(String messageId) {
    }
}
