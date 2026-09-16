package com.lootfast.backend.auth.social;

import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.config.SocialAuthProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OidcSocialIdentityTokenVerifierTests {

    @Mock private JwtDecoder googleDecoder;
    @Mock private JwtDecoder appleDecoder;

    private OidcSocialIdentityTokenVerifier verifier;

    @BeforeEach
    void setUp() {
        SocialAuthProperties properties = new SocialAuthProperties(
                new SocialAuthProperties.Provider(
                        "https://accounts.google.com",
                        "https://www.googleapis.com/oauth2/v3/certs",
                        List.of("google-client")
                ),
                new SocialAuthProperties.Provider(
                        "https://appleid.apple.com",
                        "https://appleid.apple.com/auth/keys",
                        List.of("apple-client")
                )
        );
        verifier = new OidcSocialIdentityTokenVerifier(
                properties,
                googleDecoder,
                appleDecoder
        );
    }

    @Test
    void googleTokenRequiresMatchingIssuerAudienceVerifiedEmailAndNonce() {
        when(googleDecoder.decode("valid-google-token")).thenReturn(jwt(
                "https://accounts.google.com",
                "google-client",
                "raw-nonce",
                true
        ));

        SocialIdentityTokenVerifier.VerifiedSocialIdentity identity = verifier.verify(
                SocialProvider.GOOGLE,
                "valid-google-token",
                "raw-nonce"
        );

        assertEquals("provider-subject", identity.subject());
        assertEquals("user@gmail.com", identity.email());
    }

    @Test
    void tokenWithWrongAudienceIsRejectedEvenAfterSignatureDecode() {
        when(googleDecoder.decode("wrong-audience")).thenReturn(jwt(
                "https://accounts.google.com",
                "another-client",
                "raw-nonce",
                true
        ));

        ApiException exception = assertThrows(
                ApiException.class,
                () -> verifier.verify(
                        SocialProvider.GOOGLE,
                        "wrong-audience",
                        "raw-nonce"
                )
        );

        assertEquals(ErrorCode.INVALID_SOCIAL_TOKEN, exception.getErrorCode());
    }

    @Test
    void tokenWithUnverifiedEmailIsRejected() {
        when(googleDecoder.decode("unverified-email")).thenReturn(jwt(
                "https://accounts.google.com",
                "google-client",
                "raw-nonce",
                false
        ));

        ApiException exception = assertThrows(
                ApiException.class,
                () -> verifier.verify(
                        SocialProvider.GOOGLE,
                        "unverified-email",
                        "raw-nonce"
                )
        );

        assertEquals(ErrorCode.INVALID_SOCIAL_TOKEN, exception.getErrorCode());
    }

    @Test
    void appleTokenMatchesTheSha256NonceClaim() {
        when(appleDecoder.decode("valid-apple-token")).thenReturn(jwt(
                "https://appleid.apple.com",
                "apple-client",
                sha256Hex("raw-nonce"),
                true
        ));

        SocialIdentityTokenVerifier.VerifiedSocialIdentity identity = verifier.verify(
                SocialProvider.APPLE,
                "valid-apple-token",
                "raw-nonce"
        );

        assertEquals(SocialProvider.APPLE, identity.provider());
    }

    private Jwt jwt(
            String issuer,
            String audience,
            String nonce,
            boolean emailVerified
    ) {
        Instant now = Instant.parse("2026-09-15T04:00:00Z");
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer(issuer)
                .subject("provider-subject")
                .audience(List.of(audience))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("nonce", nonce)
                .claim("email", "User@Gmail.com")
                .claim("email_verified", emailVerified)
                .claim("given_name", "An")
                .claim("family_name", "Nguyen")
                .build();
    }

    private String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
