package com.lootfast.backend.auth.social;

import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.config.SocialAuthProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
class OidcSocialIdentityTokenVerifier implements SocialIdentityTokenVerifier {

    private static final OAuth2Error INVALID_ISSUER = new OAuth2Error(
            "invalid_token", "The social identity token issuer is invalid", null
    );
    private static final OAuth2Error INVALID_AUDIENCE = new OAuth2Error(
            "invalid_token", "The social identity token audience is invalid", null
    );

    private final SocialAuthProperties properties;
    private final JwtDecoder googleDecoder;
    private final JwtDecoder appleDecoder;

    @Autowired
    OidcSocialIdentityTokenVerifier(SocialAuthProperties properties) {
        this(
                properties,
                decoderFor(SocialProvider.GOOGLE, properties.google()),
                decoderFor(SocialProvider.APPLE, properties.apple())
        );
    }

    OidcSocialIdentityTokenVerifier(
            SocialAuthProperties properties,
            JwtDecoder googleDecoder,
            JwtDecoder appleDecoder
    ) {
        this.properties = properties;
        this.googleDecoder = googleDecoder;
        this.appleDecoder = appleDecoder;
    }

    @Override
    public VerifiedSocialIdentity verify(
            SocialProvider provider,
            String idToken,
            String rawNonce
    ) {
        SocialAuthProperties.Provider providerProperties = providerProperties(provider);
        if (providerProperties.configuredClientIds().isEmpty()) {
            throw new ApiException(
                    ErrorCode.SOCIAL_LOGIN_NOT_CONFIGURED,
                    provider + " login is not configured"
            );
        }

        Jwt jwt;
        try {
            jwt = decoder(provider).decode(idToken);
        } catch (JwtException exception) {
            throw invalidToken();
        }

        if (jwt.getIssuer() == null
                || !allowedIssuers(provider, providerProperties.issuer())
                .contains(jwt.getIssuer().toString())
                || jwt.getAudience().stream().noneMatch(
                providerProperties.configuredClientIds()::contains
        )) {
            throw invalidToken();
        }

        String expectedNonce = provider == SocialProvider.APPLE
                ? sha256Hex(rawNonce)
                : rawNonce;
        if (!MessageDigest.isEqual(
                expectedNonce.getBytes(StandardCharsets.UTF_8),
                value(jwt.getClaimAsString("nonce")).getBytes(StandardCharsets.UTF_8)
        )) {
            throw invalidToken();
        }

        String subject = value(jwt.getSubject());
        String email = value(jwt.getClaimAsString("email"))
                .trim()
                .toLowerCase(Locale.ROOT);
        if (subject.isBlank() || email.isBlank() || !claimIsTrue(jwt.getClaim("email_verified"))) {
            throw invalidToken();
        }

        return new VerifiedSocialIdentity(
                provider,
                subject,
                email,
                value(jwt.getClaimAsString("given_name")),
                value(jwt.getClaimAsString("family_name"))
        );
    }

    private static JwtDecoder decoderFor(
            SocialProvider provider,
            SocialAuthProperties.Provider providerProperties
    ) {
        NimbusJwtDecoder.JwkSetUriJwtDecoderBuilder builder = NimbusJwtDecoder
                .withJwkSetUri(providerProperties.jwkSetUri());
        if (provider == SocialProvider.APPLE) {
            builder.jwsAlgorithms(algorithms -> {
                algorithms.add(SignatureAlgorithm.RS256);
                algorithms.add(SignatureAlgorithm.ES256);
            });
        } else {
            builder.jwsAlgorithm(SignatureAlgorithm.RS256);
        }

        NimbusJwtDecoder decoder = builder.build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                issuerValidator(provider, providerProperties.issuer()),
                audienceValidator(providerProperties.configuredClientIds())
        ));
        return decoder;
    }

    private static OAuth2TokenValidator<Jwt> issuerValidator(
            SocialProvider provider,
            String configuredIssuer
    ) {
        Set<String> allowedIssuers = allowedIssuers(provider, configuredIssuer);
        return jwt -> jwt.getIssuer() != null
                && allowedIssuers.contains(jwt.getIssuer().toString())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(INVALID_ISSUER);
    }

    private static OAuth2TokenValidator<Jwt> audienceValidator(List<String> clientIds) {
        return jwt -> jwt.getAudience().stream().anyMatch(clientIds::contains)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(INVALID_AUDIENCE);
    }

    private static Set<String> allowedIssuers(
            SocialProvider provider,
            String configuredIssuer
    ) {
        Set<String> issuers = new HashSet<>();
        issuers.add(configuredIssuer);
        if (provider == SocialProvider.GOOGLE) {
            issuers.add("accounts.google.com");
        }
        return issuers;
    }

    private SocialAuthProperties.Provider providerProperties(SocialProvider provider) {
        return provider == SocialProvider.GOOGLE ? properties.google() : properties.apple();
    }

    private JwtDecoder decoder(SocialProvider provider) {
        return provider == SocialProvider.GOOGLE ? googleDecoder : appleDecoder;
    }

    private boolean claimIsTrue(Object claim) {
        return Boolean.TRUE.equals(claim)
                || "true".equalsIgnoreCase(value(claim));
    }

    private String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String value(Object value) {
        return value == null ? "" : value.toString();
    }

    private ApiException invalidToken() {
        return new ApiException(
                ErrorCode.INVALID_SOCIAL_TOKEN,
                "The social identity token is invalid or expired"
        );
    }
}
