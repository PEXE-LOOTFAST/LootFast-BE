package com.lootfast.backend.auth.session;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefreshTokenCodecTests {

    private final RefreshTokenCodec codec = new RefreshTokenCodec();

    @Test
    void issueCreatesTokenBoundToSessionWithoutStoringRawValueAsHash() {
        UUID sessionId = UUID.randomUUID();

        RefreshTokenCodec.IssuedRefreshToken token = codec.issue(sessionId);

        assertEquals(sessionId, codec.parseSessionId(token.value()));
        assertTrue(codec.matches(token.value(), token.hash()));
        assertNotEquals(token.value(), token.hash());
        assertEquals(64, token.hash().length());
    }

    @Test
    void eachIssueUsesANewRandomSecret() {
        UUID sessionId = UUID.randomUUID();

        RefreshTokenCodec.IssuedRefreshToken first = codec.issue(sessionId);
        RefreshTokenCodec.IssuedRefreshToken second = codec.issue(sessionId);

        assertNotEquals(first.value(), second.value());
        assertFalse(codec.matches(first.value(), second.hash()));
    }

    @Test
    void parseRejectsMalformedToken() {
        assertThrows(IllegalArgumentException.class, () -> codec.parseSessionId(null));
        assertThrows(IllegalArgumentException.class, () -> codec.parseSessionId(""));
        assertThrows(IllegalArgumentException.class, () -> codec.parseSessionId("not-a-token"));
        assertThrows(IllegalArgumentException.class,
                () -> codec.parseSessionId(UUID.randomUUID() + ".secret.extra"));
    }
}
