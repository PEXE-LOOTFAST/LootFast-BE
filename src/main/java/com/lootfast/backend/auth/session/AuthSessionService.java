package com.lootfast.backend.auth.session;

import com.lootfast.backend.auth.dto.RefreshTokenResponse;
import com.lootfast.backend.auth.service.JwtService;
import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.config.RefreshTokenProperties;
import com.lootfast.backend.user.entity.User;
import com.lootfast.backend.user.entity.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class AuthSessionService {

    private static final String TOKEN_TYPE = "Bearer";
    private static final String INVALID_REFRESH_TOKEN_MESSAGE = "Invalid or expired refresh token";

    private final AuthSessionRepository authSessionRepository;
    private final RefreshTokenCodec refreshTokenCodec;
    private final RefreshTokenProperties refreshTokenProperties;
    private final JwtService jwtService;
    private final Clock clock;
    private final AccountSessionGate accounts;

    public AuthSessionService(
            AuthSessionRepository authSessionRepository,
            RefreshTokenCodec refreshTokenCodec,
            RefreshTokenProperties refreshTokenProperties,
            JwtService jwtService,
            Clock clock,
            AccountSessionGate accounts
    ) {
        this.authSessionRepository = authSessionRepository;
        this.refreshTokenCodec = refreshTokenCodec;
        this.refreshTokenProperties = refreshTokenProperties;
        this.jwtService = jwtService;
        this.clock = clock;
        this.accounts = accounts;
    }

    @Transactional(timeout = 5)
    public IssuedSession createSession(User user) {
        accounts.validateNewSession(user);
        Instant now = clock.instant();
        UUID sessionId = UUID.randomUUID();
        RefreshTokenCodec.IssuedRefreshToken refreshToken = refreshTokenCodec.issue(sessionId);
        Instant expiresAt = now.plus(refreshTokenProperties.expiration());

        authSessionRepository.save(new AuthSession(
                sessionId,
                user,
                refreshToken.hash(),
                expiresAt,
                now
        ));

        return new IssuedSession(
                refreshToken.value(),
                refreshTokenProperties.expiration().toSeconds()
        );
    }

    @Transactional(timeout = 5)
    public RefreshTokenResponse refresh(String rawRefreshToken) {
        UUID sessionId = parseSessionId(rawRefreshToken);
        Instant now = clock.instant();

        accounts.lockSessionOwner(sessionId);
        AuthSession session = authSessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(this::invalidRefreshToken);

        if (!session.isActiveAt(now)
                || !refreshTokenCodec.matches(rawRefreshToken, session.getRefreshTokenHash())
                || session.getUser().getStatus() != UserStatus.VERIFIED) {
            throw invalidRefreshToken();
        }

        JwtService.IssuedToken accessToken = jwtService.issueAccessToken(session.getUser());
        RefreshTokenCodec.IssuedRefreshToken rotatedToken = refreshTokenCodec.issue(sessionId);
        session.rotate(rotatedToken.hash(), now);

        long refreshExpiresIn = Math.max(
                0,
                Duration.between(now, session.getExpiresAt()).toSeconds()
        );

        return new RefreshTokenResponse(
                accessToken.value(),
                rotatedToken.value(),
                TOKEN_TYPE,
                accessToken.expiresInSeconds(),
                refreshExpiresIn
        );
    }

    @Transactional(timeout = 5)
    public void logout(String rawRefreshToken) {
        UUID sessionId;
        try {
            sessionId = refreshTokenCodec.parseSessionId(rawRefreshToken);
        } catch (IllegalArgumentException exception) {
            return;
        }

        try {
            accounts.lockSessionOwner(sessionId);
        } catch (ApiException exception) {
            if (exception.getErrorCode() == ErrorCode.INVALID_REFRESH_TOKEN) return;
            throw exception;
        }
        authSessionRepository.findByIdForUpdate(sessionId)
                .filter(session -> refreshTokenCodec.matches(
                        rawRefreshToken,
                        session.getRefreshTokenHash()
                ))
                .ifPresent(session -> session.revoke(clock.instant()));
    }

    private UUID parseSessionId(String rawRefreshToken) {
        try {
            return refreshTokenCodec.parseSessionId(rawRefreshToken);
        } catch (IllegalArgumentException exception) {
            throw invalidRefreshToken();
        }
    }

    private ApiException invalidRefreshToken() {
        return new ApiException(ErrorCode.INVALID_REFRESH_TOKEN, INVALID_REFRESH_TOKEN_MESSAGE);
    }

    public record IssuedSession(String refreshToken, long expiresInSeconds) {
    }
}
