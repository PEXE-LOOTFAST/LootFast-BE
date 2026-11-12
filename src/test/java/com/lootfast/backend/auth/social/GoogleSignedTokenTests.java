package com.lootfast.backend.auth.social;

import com.lootfast.backend.auth.dto.SocialLoginRequest;
import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.config.SocialAuthProperties;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Real RS256 signatures and HTTP JWKS, with no external Google account/network dependency. */
class GoogleSignedTokenTests {
    private static final String CLIENT = "test-web-client.apps.googleusercontent.com";
    private static final String NONCE = "one-time-backend-nonce";
    private static final AtomicInteger jwksRequests = new AtomicInteger();
    private static HttpServer server;
    private static RSAKey key;
    private OidcSocialIdentityTokenVerifier verifier;

    @BeforeAll
    static void startJwks() throws Exception {
        key = new RSAKeyGenerator(2048).keyID("google-test-key").generate();
        byte[] jwks = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/certs", exchange -> {
            jwksRequests.incrementAndGet();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, jwks.length);
            try (var out = exchange.getResponseBody()) { out.write(jwks); }
        });
        server.start();
    }

    @AfterAll
    static void stopJwks() { if (server != null) server.stop(0); }

    @BeforeEach
    void setUp() { verifier = new OidcSocialIdentityTokenVerifier(properties(List.of(CLIENT))); }

    @Test
    void validGoogleSignatureProducesIdentityAndCachesJwks() throws Exception {
        int before = jwksRequests.get();
        String token = sign(claims());
        var identity = verifier.verify(SocialProvider.GOOGLE, token, NONCE);
        assertEquals("google-subject", identity.subject());
        assertEquals("user@gmail.com", identity.email());
        assertTrue(identity.authoritativeEmail());
        verifier.verify(SocialProvider.GOOGLE, token, NONCE);
        assertEquals(before + 1, jwksRequests.get());
    }

    @Test
    void tamperedSignatureIsRejected() throws Exception {
        RSAKey attacker = new RSAKeyGenerator(2048).keyID(key.getKeyID()).generate();
        var jwt = new SignedJWT(header(), claims().build());
        jwt.sign(new RSASSASigner(attacker));
        rejected(jwt.serialize());
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        rejected(sign(claims().issueTime(Date.from(Instant.now().minusSeconds(600)))
                .expirationTime(Date.from(Instant.now().minusSeconds(300)))));
    }

    @Test
    void missingExpiryIsRejected() throws Exception {
        rejected(sign(claims().expirationTime(null)));
    }

    @Test
    void wrongAudienceIsRejected() throws Exception {
        rejected(sign(claims().audience("someone-else.apps.googleusercontent.com")));
    }

    @Test
    void wrongIssuerIsRejected() throws Exception {
        rejected(sign(claims().issuer("https://attacker.example")));
    }

    @Test
    void wrongNonceIsRejected() throws Exception {
        rejected(sign(claims().claim("nonce", "another-login")));
    }

    @Test
    void unverifiedEmailIsRejected() throws Exception {
        rejected(sign(claims().claim("email_verified", false)));
    }

    @Test
    void missingConfigurationDoesNotFetchKeys() throws Exception {
        verifier = new OidcSocialIdentityTokenVerifier(properties(List.of()));
        int before = jwksRequests.get();
        var error = assertThrows(ApiException.class,
                () -> verifier.verify(SocialProvider.GOOGLE, sign(claims()), NONCE));
        assertEquals(ErrorCode.SOCIAL_LOGIN_NOT_CONFIGURED, error.getErrorCode());
        assertEquals(before, jwksRequests.get());
    }

    @Test
    void thirdPartyEmailIsNotAuthoritativeWithoutWorkspaceClaim() throws Exception {
        var identity = verifier.verify(SocialProvider.GOOGLE,
                sign(claims().claim("email", "user@example.com")), NONCE);
        assertFalse(identity.authoritativeEmail());
        var workspace = verifier.verify(SocialProvider.GOOGLE,
                sign(claims().claim("email", "user@example.com").claim("hd", "example.com")), NONCE);
        assertTrue(workspace.authoritativeEmail());
    }

    @Test
    void credentialsAreNotExposedByRequestToString() {
        var request = new SocialLoginRequest(SocialProvider.GOOGLE, "private-token", "private-nonce",
                null, null, null, null, null, null);
        assertFalse(request.toString().contains("private-token"));
        assertFalse(request.toString().contains("private-nonce"));
    }

    private void rejected(String token) {
        var error = assertThrows(ApiException.class,
                () -> verifier.verify(SocialProvider.GOOGLE, token, NONCE));
        assertEquals(ErrorCode.INVALID_SOCIAL_TOKEN, error.getErrorCode());
    }

    private static SocialAuthProperties properties(List<String> clients) {
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/certs";
        return new SocialAuthProperties(
                new SocialAuthProperties.Provider("https://accounts.google.com", url, clients),
                new SocialAuthProperties.Provider("https://appleid.apple.com", url, List.of()));
    }

    private static JWTClaimsSet.Builder claims() {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder().issuer("https://accounts.google.com")
                .subject("google-subject").audience(CLIENT).issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300))).claim("nonce", NONCE)
                .claim("email", "User@gmail.com").claim("email_verified", true)
                .claim("given_name", "An").claim("family_name", "Nguyen");
    }

    private static JWSHeader header() {
        return new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT)
                .keyID(key.getKeyID()).build();
    }

    private static String sign(JWTClaimsSet.Builder claims) throws Exception {
        var jwt = new SignedJWT(header(), claims.build());
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }
}
