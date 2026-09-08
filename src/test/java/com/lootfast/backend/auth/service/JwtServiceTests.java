package com.lootfast.backend.auth.service;

import com.lootfast.backend.config.JwtProperties;
import com.lootfast.backend.user.entity.User;
import com.lootfast.backend.user.entity.UserRole;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtServiceTests {

    private static final String TEST_SECRET =
            "dGVzdC1qd3Qtc2VjcmV0LW11c3QtYmUtYXQtbGVhc3QtMzItYnl0ZXMtbG9uZw==";

    @Test
    void issueAccessTokenCreatesSignedTokenWithExpectedClaims() {
        SecretKey secretKey = new SecretKeySpec(
                Base64.getDecoder().decode(TEST_SECRET),
                "HmacSHA256"
        );
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(jwt -> OAuth2TokenValidatorResult.success());
        Instant issuedAt = Instant.parse("2026-09-08T08:00:00Z");
        Clock clock = Clock.fixed(issuedAt, ZoneOffset.UTC);
        JwtService jwtService = new JwtService(
                encoder,
                new JwtProperties(TEST_SECRET, Duration.ofMinutes(15)),
                clock
        );
        User user = mock(User.class);
        when(user.getId()).thenReturn(42L);
        when(user.getEmail()).thenReturn("kien@gmail.com");
        when(user.getRole()).thenReturn(UserRole.BUYER);

        JwtService.IssuedToken issuedToken = jwtService.issueAccessToken(user);
        Jwt jwt = decoder.decode(issuedToken.value());

        assertEquals(900, issuedToken.expiresInSeconds());
        assertEquals("lootfast-backend", jwt.getClaimAsString("iss"));
        assertEquals("42", jwt.getSubject());
        assertEquals("kien@gmail.com", jwt.getClaimAsString("email"));
        assertEquals("BUYER", jwt.getClaimAsString("role"));
        assertEquals(issuedAt, jwt.getIssuedAt());
        assertEquals(issuedAt.plusSeconds(900), jwt.getExpiresAt());
    }
}
