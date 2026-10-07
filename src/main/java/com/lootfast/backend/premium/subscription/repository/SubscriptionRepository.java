package com.lootfast.backend.premium.subscription.repository;

import com.lootfast.backend.common.response.PagedResponse;
import com.lootfast.backend.premium.catalog.dto.*;
import com.lootfast.backend.premium.dto.PremiumBenefit;
import com.lootfast.backend.premium.subscription.dto.SubscriptionResponse.*;
import com.lootfast.backend.premium.subscription.service.RenewalPolicy.Window;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;

@Repository
public class SubscriptionRepository {
  private final JdbcTemplate jdbc;

  public SubscriptionRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static Instant instant(ResultSet r, String field) throws SQLException {
    var t = r.getTimestamp(field);
    return t == null ? null : t.toInstant();
  }

  private static Set<PremiumBenefit> benefits(ResultSet r) throws SQLException {
    var a = r.getArray("benefit_codes");
    var result = EnumSet.noneOf(PremiumBenefit.class);
    try {
      for (var v : (Object[]) a.getArray()) result.add(PremiumBenefit.valueOf(v.toString()));
    } finally {
      a.free();
    }
    return Set.copyOf(result);
  }

  private static final String SELECT =
      """
select p.*,s.user_id,s.audience,
  array(select e.benefit from premium_entitlements e where e.subscription_period_id=p.id order by e.benefit) benefit_codes
from premium_subscription_periods p join premium_subscriptions s on s.id=p.subscription_id
""";
  private static final RowMapper<Period> MAP =
      (r, n) ->
          new Period(
              r.getObject("id", UUID.class),
              r.getObject("purchase_reference", UUID.class),
              PlanAudience.valueOf(r.getString("audience")),
              r.getObject("plan_revision_id", UUID.class),
              r.getInt("plan_version"),
              r.getString("plan_code"),
              r.getString("plan_name"),
              r.getLong("price_vnd"),
              "VND",
              r.getInt("duration_days"),
              benefits(r),
              r.getObject("terms_document_id", UUID.class),
              r.getString("terms_version"),
              instant(r, "confirmed_at"),
              instant(r, "starts_at"),
              instant(r, "ends_at"),
              instant(r, "created_at"));

  public Optional<Period> byPurchase(UUID reference) {
    return jdbc.query(SELECT + " where p.purchase_reference=?", MAP, reference).stream()
        .findFirst();
  }

  public Long owner(UUID reference) {
    return jdbc.queryForObject(
        "select s.user_id from premium_subscriptions s join premium_subscription_periods p on"
            + " p.subscription_id=s.id where p.purchase_reference=?",
        Long.class,
        reference);
  }

  public UUID ensure(long user, PlanAudience audience, Instant now) {
    jdbc.update(
        "insert into premium_subscriptions(id,user_id,audience,created_at) values(?,?,?,?) on"
            + " conflict(user_id,audience) do nothing",
        UUID.randomUUID(),
        user,
        audience.name(),
        Timestamp.from(now));
    return jdbc.queryForObject(
        "select id from premium_subscriptions where user_id=? and audience=? for update",
        UUID.class,
        user,
        audience.name());
  }

  public Instant paidEnd(long user, PlanAudience audience) {
    var t =
        jdbc.queryForObject(
            "select max(p.ends_at) from premium_subscription_periods p join premium_subscriptions s"
                + " on s.id=p.subscription_id where s.user_id=? and s.audience=?",
            Timestamp.class,
            user,
            audience.name());
    return t == null ? null : t.toInstant();
  }

  public boolean insert(
      UUID subscription, UUID ref, PlanResponse plan, Instant confirmed, Window w, Instant now) {
    UUID id = UUID.randomUUID();
    int inserted =
        jdbc.update(
            """
insert into premium_subscription_periods(id,subscription_id,purchase_reference,plan_revision_id,plan_version,plan_code,plan_name,price_vnd,duration_days,terms_document_id,terms_version,confirmed_at,starts_at,ends_at,created_at)
values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) on conflict(purchase_reference) do nothing
""",
            id,
            subscription,
            ref,
            plan.id(),
            plan.version(),
            plan.code(),
            plan.name(),
            plan.priceVnd(),
            plan.durationDays(),
            plan.termsDocumentId(),
            plan.termsVersion(),
            Timestamp.from(confirmed),
            Timestamp.from(w.startsAt()),
            Timestamp.from(w.endsAt()),
            Timestamp.from(now));
    if (inserted == 0) return false;
    Long user =
        jdbc.queryForObject(
            "select user_id from premium_subscriptions where id=?", Long.class, subscription);
    jdbc.batchUpdate(
        """
insert into premium_entitlements(id,user_id,benefit,starts_at,ends_at,granted_at,granted_by,grant_reason,source,subscription_period_id)
values(?,?,?,?,?,?,null,'Confirmed Premium purchase','PURCHASE',?)
""",
        plan.benefits().stream()
            .map(
                benefit ->
                    new Object[] {
                      UUID.randomUUID(),
                      user,
                      benefit.name(),
                      Timestamp.from(w.startsAt()),
                      Timestamp.from(w.endsAt()),
                      Timestamp.from(now),
                      id
                    })
            .toList());
    return true;
  }

  public List<Coverage> coverage(long user, Instant now) {
    return jdbc.query(
        """
        with grants as (
          select benefit,source,starts_at,ends_at from premium_entitlements
          where user_id=? and revoked_at is null and ends_at>?
        ), grouped as (
          select benefit,range_agg(tstzrange(starts_at,ends_at,'[)')) windows,
            array_agg(distinct source) filter(where starts_at<=?) sources
          from grants group by benefit
        )
        select benefit,sources,upper(span) effective_until from grouped,unnest(windows) span
        where span @> cast(? as timestamptz)
        """,
        (r, n) -> {
          var a = r.getArray("sources");
          var sources = new HashSet<String>();
          try {
            for (var v : (Object[]) a.getArray()) sources.add(v.toString());
          } finally {
            a.free();
          }
          return new Coverage(
              PremiumBenefit.valueOf(r.getString("benefit")),
              Set.copyOf(sources),
              instant(r, "effective_until"));
        },
        user,
        Timestamp.from(now),
        Timestamp.from(now),
        Timestamp.from(now));
  }

  public PagedResponse<Period> periods(long user, PlanAudience audience, int page, int size) {
    Long total =
        jdbc.queryForObject(
            "select count(*) from premium_subscription_periods p join premium_subscriptions s on"
                + " s.id=p.subscription_id where s.user_id=? and s.audience=?",
            Long.class,
            user,
            audience.name());
    var rows =
        jdbc.query(
            SELECT
                + " where s.user_id=? and s.audience=? order by p.starts_at desc,p.id limit ?"
                + " offset ?",
            MAP,
            user,
            audience.name(),
            size,
            (long) page * size);
    return new PagedResponse<>(
        rows, page, size, total, total == 0 ? 0 : Math.toIntExact((total + size - 1) / size));
  }
}
