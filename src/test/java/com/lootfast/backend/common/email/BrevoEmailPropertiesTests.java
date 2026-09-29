package com.lootfast.backend.common.email;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrevoEmailPropertiesTests {

    @Test
    void acceptsValidConfigurationWithoutExposingApiKeyInToString() {
        BrevoEmailProperties properties = assertDoesNotThrow(() -> properties(
                "https://api.brevo.com/v3/",
                "secret-api-key",
                "lootfast.app@gmail.com"
        ));

        assertTrue(properties.baseUrl().endsWith("/v3"));
        assertTrue(properties.toString().contains("<redacted>"));
        assertTrue(!properties.toString().contains("secret-api-key"));
    }

    @Test
    void rejectsBlankApiKey() {
        assertThrows(IllegalStateException.class, () -> properties(
                "https://api.brevo.com/v3",
                " ",
                "lootfast.app@gmail.com"
        ));
    }

    @Test
    void rejectsNonHttpsBaseUrl() {
        assertThrows(IllegalStateException.class, () -> properties(
                "http://api.brevo.com/v3",
                "secret-api-key",
                "lootfast.app@gmail.com"
        ));
    }

    @Test
    void rejectsInvalidSenderEmail() {
        assertThrows(IllegalStateException.class, () -> properties(
                "https://api.brevo.com/v3",
                "secret-api-key",
                "not-an-email"
        ));
    }

    private BrevoEmailProperties properties(String baseUrl, String apiKey, String fromEmail) {
        return new BrevoEmailProperties(
                baseUrl,
                apiKey,
                fromEmail,
                "LootFast",
                Duration.ofSeconds(3),
                Duration.ofSeconds(10)
        );
    }
}
