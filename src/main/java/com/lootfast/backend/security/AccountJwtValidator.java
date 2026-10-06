package com.lootfast.backend.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class AccountJwtValidator implements OAuth2TokenValidator<Jwt> {
  private final JdbcTemplate jdbc;

  public AccountJwtValidator(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public OAuth2TokenValidatorResult validate(Jwt jwt) {
    Long id;
    long version;
    try {
      id = Long.valueOf(jwt.getSubject());
      Number claim = jwt.getClaim("auth_version");
      version = claim == null ? 0 : claim.longValue();
    } catch (RuntimeException exception) {
      return rejected();
    }
    var rows = jdbc.query("select locked_at, authentication_version from users where id=?",
        (rs, row) -> rs.getTimestamp("locked_at") == null
            && rs.getLong("authentication_version") == version, id);
    return rows.size() == 1 && rows.getFirst()
        ? OAuth2TokenValidatorResult.success() : rejected();
  }

  private OAuth2TokenValidatorResult rejected() {
    return OAuth2TokenValidatorResult.failure(
        new OAuth2Error("invalid_token", "Account access is unavailable", null));
  }
}
