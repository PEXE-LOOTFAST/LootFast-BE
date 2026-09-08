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

@Component
public class JwtRoleConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String ROLE_PREFIX = "ROLE_";

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        List<GrantedAuthority> authorities = toAuthorities(jwt.getClaimAsString("role"));
        return new JwtAuthenticationToken(jwt, authorities, jwt.getClaimAsString("email"));
    }

    private List<GrantedAuthority> toAuthorities(String roleClaim) {
        if (roleClaim == null || roleClaim.isBlank()) {
            return List.of();
        }

        try {
            UserRole role = UserRole.valueOf(roleClaim);
            return List.of(new SimpleGrantedAuthority(ROLE_PREFIX + role.name()));
        } catch (IllegalArgumentException exception) {
            return List.of();
        }
    }
}
