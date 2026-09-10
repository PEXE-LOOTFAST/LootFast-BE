package com.lootfast.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "security.refresh-token")
public record RefreshTokenProperties(Duration expiration) {

    public RefreshTokenProperties {
        if (expiration == null || expiration.isZero() || expiration.isNegative()) {
            throw new IllegalStateException("Refresh token expiration must be greater than zero");
        }
    }
}
