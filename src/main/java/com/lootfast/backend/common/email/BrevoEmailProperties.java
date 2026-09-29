package com.lootfast.backend.common.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties(prefix = "email.brevo")
public final class BrevoEmailProperties {

    private final String baseUrl;
    private final String apiKey;
    private final String fromEmail;
    private final String fromName;
    private final Duration connectTimeout;
    private final Duration readTimeout;

    public BrevoEmailProperties(
            String baseUrl,
            String apiKey,
            String fromEmail,
            String fromName,
            Duration connectTimeout,
            Duration readTimeout
    ) {
        this.baseUrl = requireHttpsUrl(baseUrl);
        this.apiKey = requireText(apiKey, "BREVO_API_KEY must not be blank");
        this.fromEmail = requireEmail(fromEmail);
        this.fromName = requireText(fromName, "BREVO_FROM_NAME must not be blank");
        this.connectTimeout = requirePositive(connectTimeout, "BREVO_CONNECT_TIMEOUT");
        this.readTimeout = requirePositive(readTimeout, "BREVO_READ_TIMEOUT");
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String apiKey() {
        return apiKey;
    }

    public String fromEmail() {
        return fromEmail;
    }

    public String fromName() {
        return fromName;
    }

    public Duration connectTimeout() {
        return connectTimeout;
    }

    public Duration readTimeout() {
        return readTimeout;
    }

    @Override
    public String toString() {
        return "BrevoEmailProperties[baseUrl=%s, apiKey=<redacted>, fromEmail=%s, fromName=%s, connectTimeout=%s, readTimeout=%s]"
                .formatted(baseUrl, fromEmail, fromName, connectTimeout, readTimeout);
    }

    private static String requireHttpsUrl(String value) {
        String url = requireText(value, "BREVO_BASE_URL must not be blank");
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("BREVO_BASE_URL must be a valid HTTPS URL", exception);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new IllegalStateException("BREVO_BASE_URL must be a valid HTTPS URL");
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String requireEmail(String value) {
        String email = requireText(value, "BREVO_FROM_EMAIL must not be blank");
        int at = email.indexOf('@');
        if (at <= 0 || at == email.length() - 1 || email.indexOf('@', at + 1) >= 0) {
            throw new IllegalStateException("BREVO_FROM_EMAIL must be a valid email address");
        }
        return email;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(message);
        }
        return value.trim();
    }

    private static Duration requirePositive(Duration value, String variableName) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException(variableName + " must be greater than zero");
        }
        return value;
    }
}
