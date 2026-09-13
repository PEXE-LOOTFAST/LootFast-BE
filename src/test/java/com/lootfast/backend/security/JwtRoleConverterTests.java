package com.lootfast.backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtRoleConverterTests {

    private final JwtRoleConverter converter = new JwtRoleConverter();

    @Test
    void convertMapsKnownRoleToSpringAuthority() {
        AbstractAuthenticationToken authentication = converter.convert(jwtWithRole("MERCHANT"));

        assertEquals("merchant@gmail.com", authentication.getName());
        assertTrue(authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_MERCHANT")));
    }

    @Test
    void convertDoesNotGrantAuthorityForUnknownRole() {
        AbstractAuthenticationToken authentication = converter.convert(jwtWithRole("SUPER_ADMIN"));

        assertTrue(authentication.getAuthorities().isEmpty());
    }

    @Test
    void convertMapsEveryKnownRoleFromNewRolesClaim() {
        Instant now = Instant.parse("2026-09-08T08:00:00Z");
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("1")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(900))
                .claim("email", "merchant@gmail.com")
                .claim("roles", java.util.List.of("BUYER", "MERCHANT"))
                .build();

        AbstractAuthenticationToken authentication = converter.convert(jwt);

        assertTrue(authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_BUYER")));
        assertTrue(authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_MERCHANT")));
    }

    private Jwt jwtWithRole(String role) {
        Instant now = Instant.parse("2026-09-08T08:00:00Z");
        return Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("1")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(900))
                .claim("email", "merchant@gmail.com")
                .claim("role", role)
                .build();
    }
}
