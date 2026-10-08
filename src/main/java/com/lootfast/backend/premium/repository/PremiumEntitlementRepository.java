package com.lootfast.backend.premium.repository;

import com.lootfast.backend.common.response.PagedResponse;
import com.lootfast.backend.premium.dto.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;

@Repository
public class PremiumEntitlementRepository {
  private final JdbcTemplate jdbc;

  public PremiumEntitlementRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public boolean active(long user, Instant now) {
    return active(user, PremiumBenefit.MERCHANT_ADVANCED_REPORTS, now);
  }

  public boolean active(long user, PremiumBenefit benefit, Instant now) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            """
select exists(select 1 from premium_entitlements where user_id=? and benefit=?
 and revoked_at is null and starts_at<=? and ends_at>?)
""",
            Boolean.class,
            user,
            benefit.name(),
            Timestamp.from(now),
            Timestamp.from(now)));
  }

  private static final String SELECT =
      "select e.*,u.public_id as user_public_id from premium_entitlements e join users u on"
          + " u.id=e.user_id ";

  private static Instant instant(ResultSet r, String column) throws SQLException {
    Timestamp value = r.getTimestamp(column);
    return value == null ? null : value.toInstant();
  }

  private static final RowMapper<PremiumEntitlement> MAPPER =
      (r, n) ->
          new PremiumEntitlement(
              r.getObject("id", UUID.class),
              r.getObject("user_public_id", UUID.class),
              r.getString("benefit"),
              instant(r, "starts_at"),
              instant(r, "ends_at"),
              instant(r, "granted_at"),
              r.getObject("granted_by", UUID.class),
              r.getString("grant_reason"),
              instant(r, "revoked_at"),
              r.getObject("revoked_by", UUID.class),
              r.getString("revoke_reason"),
              r.getString("source"),
              r.getObject("subscription_period_id", UUID.class));

  public Optional<PremiumEntitlement> get(UUID id) {
    return jdbc.query(SELECT + "where e.id=?", MAPPER, id).stream().findFirst();
  }

  public void insert(long user, UUID actor, GrantPremiumRequest r, Instant now) {
    jdbc.update(
        """
insert into premium_entitlements(id,user_id,benefit,starts_at,ends_at,granted_at,granted_by,grant_reason)
values (?,?,?,?,?,?,?,?) on conflict(id) do nothing
""",
        r.id(),
        user,
        r.benefit().name(),
        Timestamp.from(r.startsAt()),
        Timestamp.from(r.endsAt()),
        Timestamp.from(now),
        actor,
        r.reason().trim());
  }

  public void revoke(UUID id, UUID actor, String reason, Instant now) {
    jdbc.update(
        """
update premium_entitlements set revoked_at=?,revoked_by=?,revoke_reason=? where id=? and revoked_at is null and source='MANUAL'
""",
        Timestamp.from(now),
        actor,
        reason,
        id);
  }

  public PagedResponse<PremiumEntitlement> list(long user, int page, int size) {
    Long count =
        jdbc.queryForObject(
            "select count(*) from premium_entitlements where user_id=?", Long.class, user);
    var rows =
        jdbc.query(
            SELECT + "where e.user_id=? order by e.granted_at desc,e.id limit ? offset ?",
            MAPPER,
            user,
            size,
            (long) page * size);
    return new PagedResponse<>(
        rows, page, size, count, count == 0 ? 0 : Math.toIntExact((count + size - 1) / size));
  }
}
