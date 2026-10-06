package com.lootfast.backend.auth.session;

import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.user.entity.User;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AccountSessionGate {
  private final JdbcTemplate jdbc;

  public AccountSessionGate(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void validateNewSession(User user) {
    var rows = jdbc.query(
        "select locked_at, authentication_version from users where id=? for update",
        (rs, row) -> rs.getTimestamp("locked_at") == null
            && rs.getLong("authentication_version") == user.getAuthenticationVersion(),
        user.getId());
    if (rows.size() != 1 || !rows.getFirst()) {
      throw new ApiException(ErrorCode.ACCOUNT_LOCKED);
    }
  }

  // Logout catches this read-only rejection and remains idempotent. Do not mark its
  // transaction rollback-only; refresh still rolls back when the exception escapes.
  @Transactional(propagation = Propagation.MANDATORY, noRollbackFor = ApiException.class)
  public void lockSessionOwner(UUID sessionId) {
    // Always user before session, matching admin lock/revocation and session creation.
    var rows = jdbc.query("""
        select u.locked_at from users u
        where u.id=(select s.user_id from auth_sessions s where s.id=?) for update
        """, (rs, row) -> rs.getTimestamp("locked_at") == null, sessionId);
    if (rows.size() != 1 || !rows.getFirst()) {
      throw new ApiException(ErrorCode.INVALID_REFRESH_TOKEN);
    }
  }
}
