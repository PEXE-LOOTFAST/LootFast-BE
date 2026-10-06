package com.lootfast.backend.auth.session;

import com.lootfast.backend.auth.dto.RefreshTokenResponse;
import com.lootfast.backend.auth.service.JwtService;
import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.config.RefreshTokenProperties;
import com.lootfast.backend.user.entity.User;
import com.lootfast.backend.user.entity.UserRole;
import com.lootfast.backend.user.entity.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthSessionServiceTests {

    private static final Instant NOW = Instant.parse("2026-09-10T08:00:00Z");
    private static final Duration EXPIRATION = Duration.ofDays(30);

    @Mock private AuthSessionRepository authSessionRepository;
    @Mock private RefreshTokenCodec refreshTokenCodec;
    @Mock private JwtService jwtService;
    @Mock private AccountSessionGate accounts;

    private AuthSessionService authSessionService;

    @BeforeEach
    void setUp() {
        authSessionService = new AuthSessionService(
                authSessionRepository,
                refreshTokenCodec,
                new RefreshTokenProperties(EXPIRATION),
                jwtService,
                Clock.fixed(NOW, ZoneOffset.UTC),
                accounts
        );
    }

    @Test
    void createSessionStoresOnlyHashAndReturnsRawRefreshToken() {
        User user = verifiedUser();
        when(refreshTokenCodec.issue(any(UUID.class)))
                .thenReturn(new RefreshTokenCodec.IssuedRefreshToken("raw-token", "token-hash"));

        AuthSessionService.IssuedSession result = authSessionService.createSession(user);

        ArgumentCaptor<AuthSession> captor = ArgumentCaptor.forClass(AuthSession.class);
        verify(authSessionRepository).save(captor.capture());
        assertEquals("raw-token", result.refreshToken());
        assertEquals(EXPIRATION.toSeconds(), result.expiresInSeconds());
        assertEquals("token-hash", captor.getValue().getRefreshTokenHash());
        assertEquals(NOW.plus(EXPIRATION), captor.getValue().getExpiresAt());
    }

    @Test
    void refreshRotatesTokenAndKeepsAbsoluteSessionExpiration() {
        UUID sessionId = UUID.randomUUID();
        User user = verifiedUser();
        AuthSession session = session(sessionId, user, "old-hash", NOW.plus(EXPIRATION));
        String oldToken = sessionId + ".old-secret";
        when(refreshTokenCodec.parseSessionId(oldToken)).thenReturn(sessionId);
        when(authSessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session));
        when(refreshTokenCodec.matches(oldToken, "old-hash")).thenReturn(true);
        when(jwtService.issueAccessToken(user))
                .thenReturn(new JwtService.IssuedToken("new-access-token", 900));
        when(refreshTokenCodec.issue(sessionId))
                .thenReturn(new RefreshTokenCodec.IssuedRefreshToken("new-refresh-token", "new-hash"));

        RefreshTokenResponse response = authSessionService.refresh(oldToken);

        assertEquals("new-access-token", response.accessToken());
        assertEquals("new-refresh-token", response.refreshToken());
        assertEquals(900, response.expiresIn());
        assertEquals(EXPIRATION.toSeconds(), response.refreshExpiresIn());
        assertEquals("new-hash", session.getRefreshTokenHash());
        assertEquals(NOW.plus(EXPIRATION), session.getExpiresAt());
    }

    @Test
    void refreshRejectsExpiredSessionWithoutIssuingTokens() {
        UUID sessionId = UUID.randomUUID();
        AuthSession session = session(sessionId, verifiedUser(), "old-hash", NOW);
        String rawToken = sessionId + ".old-secret";
        when(refreshTokenCodec.parseSessionId(rawToken)).thenReturn(sessionId);
        when(authSessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session));

        ApiException exception = assertThrows(
                ApiException.class,
                () -> authSessionService.refresh(rawToken)
        );

        assertEquals(ErrorCode.INVALID_REFRESH_TOKEN, exception.getErrorCode());
        verify(jwtService, never()).issueAccessToken(any());
        verify(refreshTokenCodec, never()).issue(any());
    }

    @Test
    void refreshRejectsTokenThatDoesNotMatchStoredHash() {
        UUID sessionId = UUID.randomUUID();
        AuthSession session = session(sessionId, verifiedUser(), "stored-hash", NOW.plus(EXPIRATION));
        String rawToken = sessionId + ".wrong-secret";
        when(refreshTokenCodec.parseSessionId(rawToken)).thenReturn(sessionId);
        when(authSessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session));
        when(refreshTokenCodec.matches(rawToken, "stored-hash")).thenReturn(false);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> authSessionService.refresh(rawToken)
        );

        assertEquals(ErrorCode.INVALID_REFRESH_TOKEN, exception.getErrorCode());
        verify(jwtService, never()).issueAccessToken(any());
    }

    @Test
    void logoutRevokesMatchingSession() {
        UUID sessionId = UUID.randomUUID();
        AuthSession session = session(sessionId, verifiedUser(), "stored-hash", NOW.plus(EXPIRATION));
        String rawToken = sessionId + ".secret";
        when(refreshTokenCodec.parseSessionId(rawToken)).thenReturn(sessionId);
        when(authSessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session));
        when(refreshTokenCodec.matches(rawToken, "stored-hash")).thenReturn(true);

        authSessionService.logout(rawToken);

        assertEquals(NOW, session.getRevokedAt());
    }

    @Test
    void logoutIsIdempotentForMalformedOrMissingSession() {
        when(refreshTokenCodec.parseSessionId("malformed"))
                .thenThrow(new IllegalArgumentException("bad token"));

        authSessionService.logout("malformed");

        verify(authSessionRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void logoutDoesNotRevokeSessionWhenSecretDoesNotMatch() {
        UUID sessionId = UUID.randomUUID();
        AuthSession session = session(sessionId, verifiedUser(), "stored-hash", NOW.plus(EXPIRATION));
        String rawToken = sessionId + ".wrong-secret";
        when(refreshTokenCodec.parseSessionId(rawToken)).thenReturn(sessionId);
        when(authSessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session));
        when(refreshTokenCodec.matches(rawToken, "stored-hash")).thenReturn(false);

        authSessionService.logout(rawToken);

        assertNull(session.getRevokedAt());
        assertNotNull(session.getRefreshTokenHash());
    }

    private AuthSession session(UUID id, User user, String hash, Instant expiresAt) {
        return new AuthSession(id, user, hash, expiresAt, NOW.minusSeconds(60));
    }

    private User verifiedUser() {
        return new User(
                "Kien",
                "Nguyen Van",
                "kien@gmail.com",
                "912345678",
                "encoded-password",
                UserRole.BUYER,
                UserStatus.VERIFIED
        );
    }
}
