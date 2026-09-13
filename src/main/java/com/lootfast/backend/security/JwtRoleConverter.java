package com.lootfast.backend.security;

import com.lootfast.backend.user.entity.UserRole;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class JwtRoleConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String ROLE_PREFIX = "ROLE_";

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        List<String> roleClaims = jwt.getClaimAsStringList("roles");
        if (roleClaims == null || roleClaims.isEmpty()) {
            String legacyRole = jwt.getClaimAsString("role");
            roleClaims = legacyRole == null ? List.of() : List.of(legacyRole);
        }
        List<GrantedAuthority> authorities = roleClaims.stream()
                .map(this::toAuthority)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return new JwtAuthenticationToken(jwt, authorities, jwt.getClaimAsString("email"));
    }

    private GrantedAuthority toAuthority(String roleClaim) {
        if (roleClaim == null || roleClaim.isBlank()) {
            return null;
        }

        try {
            UserRole role = UserRole.valueOf(roleClaim);
            return new SimpleGrantedAuthority(ROLE_PREFIX + role.name());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
