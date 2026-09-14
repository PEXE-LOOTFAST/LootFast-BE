package com.lootfast.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null
                ? List.of()
                : allowedOrigins.stream()
                        .map(String::trim)
                        .filter(origin -> !origin.isEmpty())
                        .toList();

        if (allowedOrigins.contains("*")) {
            throw new IllegalStateException(
                    "CORS allowed origins must list explicit origins instead of '*'"
            );
        }
    }
}
