package com.lootfast.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "security.social")
public record SocialAuthProperties(
        Provider google,
        Provider apple
) {
    public record Provider(
            String issuer,
            String jwkSetUri,
            List<String> clientIds
    ) {
        public List<String> configuredClientIds() {
            if (clientIds == null) {
                return List.of();
            }
            return clientIds.stream()
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .toList();
        }
    }
}
