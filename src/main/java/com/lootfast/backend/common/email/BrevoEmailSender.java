package com.lootfast.backend.common.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "email.provider", havingValue = "brevo", matchIfMissing = true)
class BrevoEmailSender implements EmailSender {

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

    private void sendOtp(String to, String otp, String purpose, String subject, String heading) {
        BrevoEmailRequest request = new BrevoEmailRequest(
                new Sender(properties.fromName(), properties.fromEmail()),
                List.of(new Recipient(to)),
                subject,
                buildBody(otp, heading),
                Map.of("idempotencyKey", idempotencyKey(purpose, to, otp)),
                List.of("otp", purpose)
        );

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

                log.info("OTP email accepted by Brevo, messageId={}", sanitizeForLog(response.messageId()));
                return;
            } catch (RestClientResponseException exception) {
                if (attempt < MAX_ATTEMPTS && exception.getStatusCode().is5xxServerError()) {
                    if (!backoffBeforeRetry(attempt)) {
                        return;
                    }
                    continue;
                }
                log.error(
                        "Brevo rejected OTP email with HTTP status {}",
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
                log.error("Brevo OTP request failed after {} attempts", attempt, exception);
                return;
            } catch (RestClientException exception) {
                log.error("Brevo OTP request failed", exception);
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
