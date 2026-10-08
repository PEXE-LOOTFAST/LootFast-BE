package com.lootfast.backend.buyer.savedfilter.notification;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

@Repository
public class SavedFilterRecipientRepository {
  private final NamedParameterJdbcTemplate jdbc;

  public SavedFilterRecipientRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  // All clauses are shared between initial matching and the email's eligibility recheck.
  private static final String ELIGIBLE =
      """
from users u join user_roles r on r.user_id=u.id and r.role='BUYER'
  cross join deals d join merchant_profiles m on m.id=d.merchant_profile_id
  join users owner on owner.id=m.user_id
  left join notification_preferences p on p.user_id=u.id
where d.id=:deal and u.id<>m.user_id and u.status='VERIFIED' and u.locked_at is null
  and owner.locked_at is null and m.store_name is not null and m.latitude is not null and m.longitude is not null
  and (m.suspended_until is null or m.suspended_until<=:now)
  and d.status='ACTIVE' and d.remaining_quantity>0 and d.available_from<=:now and d.available_until>:now and d.pickup_end>:now
  and coalesce(p.matching_deals_enabled,true)
  and exists(select 1 from premium_entitlements e where e.user_id=u.id and e.benefit='BUYER_SAVED_FILTERS'
    and e.revoked_at is null and e.starts_at<=:now and e.ends_at>:now)
  and exists(select 1 from buyer_saved_filters f where f.user_id=u.id and f.notifications_enabled
    and f.notifications_enabled_at<=:eventAt
    and (f.category_code is null or f.category_code=d.category_code)
    and (f.max_price is null or d.discounted_price<=f.max_price)
    and (f.min_discount_percent is null or (d.original_price-d.discounted_price)*100>=d.original_price*f.min_discount_percent)
    and (f.search_keyword is null or
        d.search_name like '%'||replace(replace(replace(f.search_keyword,'!','!!'),'%','!%'),'_','!_')||'%' escape '!'
        or m.search_name like '%'||replace(replace(replace(f.search_keyword,'!','!!'),'%','!%'),'_','!_')||'%' escape '!')
    and (f.pickup_from is null or (
        d.pickup_end > (((d.pickup_start at time zone 'Asia/Ho_Chi_Minh')::date+f.pickup_from) at time zone 'Asia/Ho_Chi_Minh')
        and d.pickup_start < (((d.pickup_start at time zone 'Asia/Ho_Chi_Minh')::date+f.pickup_to) at time zone 'Asia/Ho_Chi_Minh')))
    and abs(m.latitude-f.latitude)<=degrees(f.radius_km::double precision/6371.0088)
    and 6371.0088*2*asin(sqrt(least(1.0,greatest(0.0,
        power(sin(radians((m.latitude-f.latitude)::double precision)/2),2)
        +cos(radians(f.latitude::double precision))*cos(radians(m.latitude::double precision))
        *power(sin(radians((m.longitude-f.longitude)::double precision)/2),2)))))<=f.radius_km)
""";

  public void lockRecipients(List<Long> ids, long storeOwner) {
    var recipients = ids.stream().filter(id -> id != storeOwner).toList();
    if (recipients.isEmpty()) return;
    // Filter/preference edits and entitlement revocation already lock this same users row.
    // Never lock the store owner here: publication holds its profile before recipient FK locks.
    jdbc.queryForList(
        "select id from users where id in (:ids) order by id for update",
        Map.of("ids", recipients),
        Long.class);
  }

  public List<Long> candidates(long after, Instant now, int size) {
    return jdbc.queryForList(
        "select distinct user_id from premium_entitlements where user_id>:after and"
            + " benefit='BUYER_SAVED_FILTERS' and revoked_at is null and starts_at<=:now and"
            + " ends_at>:now order by user_id limit :size",
        new MapSqlParameterSource()
            .addValue("after", after)
            .addValue("now", Timestamp.from(now))
            .addValue("size", size),
        Long.class);
  }

  public List<Long> recipients(
      long deal, long after, long through, Instant eventAt, Instant now, int size) {
    return jdbc.queryForList(
        "select u.id " + ELIGIBLE + " and u.id>:after and u.id<=:through order by u.id limit :size",
        params(deal, eventAt, now)
            .addValue("after", after)
            .addValue("through", through)
            .addValue("size", size),
        Long.class);
  }

  public boolean emailEligible(long user, UUID publicDeal, Instant now) {
    var rows =
        jdbc.query(
            """
select j.deal_id,j.event_at from premium_deal_notification_jobs j join deals d on d.id=j.deal_id where d.public_id=:publicId
""",
            Map.of("publicId", publicDeal),
            (r, n) -> new Event(r.getLong("deal_id"), r.getTimestamp("event_at").toInstant()));
    if (rows.isEmpty()) return false;
    var e = rows.getFirst();
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "select exists(select 1 " + ELIGIBLE + " and u.id=:user and p.email_enabled)",
            params(e.deal(), e.time(), now).addValue("user", user),
            Boolean.class));
  }

  private static MapSqlParameterSource params(long deal, Instant eventAt, Instant now) {
    return new MapSqlParameterSource()
        .addValue("deal", deal)
        .addValue("eventAt", Timestamp.from(eventAt))
        .addValue("now", Timestamp.from(now));
  }

  private record Event(long deal, Instant time) {}
}
