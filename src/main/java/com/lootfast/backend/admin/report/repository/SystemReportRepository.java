package com.lootfast.backend.admin.report.repository;

import com.lootfast.backend.admin.report.dto.SystemReportResponse.*;
import com.lootfast.backend.deal.entity.DealStatus;
import com.lootfast.backend.order.entity.OrderStatus;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SystemReportRepository {
  private final JdbcTemplate jdbc;

  public SystemReportRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Accounts accounts(Instant start, Instant end) {
    return jdbc.queryForObject(
        """
with accounts as (
  select u.*, exists(select 1 from user_roles r where r.user_id=u.id and r.role='BUYER') buyer,
    exists(select 1 from user_roles r where r.user_id=u.id and r.role='MERCHANT') merchant,
    exists(select 1 from user_roles r where r.user_id=u.id and r.role='ADMIN') admin
  from users u
)
select count(*) total, count(*) filter(where buyer) buyers,
  count(*) filter(where merchant) merchants, count(*) filter(where admin) admins,
  count(*) filter(where locked_at is not null) locked,
  count(*) filter(where created_at>=? and created_at<?) fresh,
  count(*) filter(where buyer and created_at>=? and created_at<?) fresh_buyers,
  count(*) filter(where merchant and created_at>=? and created_at<?) fresh_merchants
from accounts
""",
        (rs, row) ->
            new Accounts(
                rs.getLong("total"),
                rs.getLong("buyers"),
                rs.getLong("merchants"),
                rs.getLong("admins"),
                rs.getLong("locked"),
                rs.getLong("fresh"),
                rs.getLong("fresh_buyers"),
                rs.getLong("fresh_merchants")),
        Timestamp.from(start),
        Timestamp.from(end),
        Timestamp.from(start),
        Timestamp.from(end),
        Timestamp.from(start),
        Timestamp.from(end));
  }

  public Stores stores() {
    return jdbc.queryForObject(
        """
select (select count(*) from merchant_profiles) total,
  (select count(*) from merchant_profiles m join merchant_applications a on a.user_id=m.user_id
     where a.status='APPROVED') approved,
  (select count(*) from merchant_applications where status='PENDING') pending
""",
        (rs, row) ->
            new Stores(rs.getLong("total"), rs.getLong("approved"), rs.getLong("pending")));
  }

  public Deals deals(Instant start, Instant end, Instant now) {
    var created = new EnumMap<DealStatus, Long>(DealStatus.class);
    var current = new EnumMap<DealStatus, Long>(DealStatus.class);
    for (var s : DealStatus.values()) {
      created.put(s, 0L);
      current.put(s, 0L);
    }
    jdbc.query(
        """
        select status, count(*) total, count(*) filter(where created_at>=? and created_at<?) fresh
        from deals group by status
        """,
        rs -> {
          var s = DealStatus.valueOf(rs.getString("status"));
          created.put(s, rs.getLong("fresh"));
          current.put(s, rs.getLong("total"));
        },
        Timestamp.from(start),
        Timestamp.from(end));
    Long open =
        jdbc.queryForObject(
            """
            select count(*) from deals d join merchant_profiles m on m.id=d.merchant_profile_id
              join users u on u.id=m.user_id
            where d.status='ACTIVE' and d.remaining_quantity>0
              and d.available_from<=? and d.available_until>? and d.pickup_end>?
              and u.locked_at is null and (m.suspended_until is null or m.suspended_until<=?)
            """,
            Long.class,
            Timestamp.from(now),
            Timestamp.from(now),
            Timestamp.from(now),
            Timestamp.from(now));
    return new Deals(
        created.values().stream().mapToLong(Long::longValue).sum(),
        Map.copyOf(created),
        Map.copyOf(current),
        open == null ? 0 : open);
  }

  public Map<OrderStatus, Long> placed(Instant start, Instant end) {
    var statuses = new EnumMap<OrderStatus, Long>(OrderStatus.class);
    for (var s : OrderStatus.values()) statuses.put(s, 0L);
    jdbc.query(
        """
select status, count(*) total from customer_orders where created_at>=? and created_at<? group by status
""",
        rs -> {
          statuses.put(OrderStatus.valueOf(rs.getString("status")), rs.getLong("total"));
        },
        Timestamp.from(start),
        Timestamp.from(end));
    return Map.copyOf(statuses);
  }

  public List<Daily> daily(Instant start, Instant end) {
    return jdbc.query(
        """
with placed as (
  select cast(created_at at time zone 'Asia/Ho_Chi_Minh' as date) as business_date, count(*) orders
  from customer_orders where created_at>=? and created_at<? group by business_date
), completed as (
  select cast(finalized_at at time zone 'Asia/Ho_Chi_Minh' as date) as business_date, count(*) orders,
    sum(case when payment_status='CONFIRMED' then total_amount else 0 end) value,
    sum(quantity) portions, sum((original_unit_price-unit_price)*quantity) savings
  from customer_orders where status='COMPLETED' and finalized_at>=? and finalized_at<? group by business_date
)
select coalesce(p.business_date,c.business_date) as business_date, coalesce(p.orders,0) placed, coalesce(c.orders,0) completed,
  coalesce(c.value,0) value, coalesce(c.portions,0) portions, coalesce(c.savings,0) savings
from placed p full outer join completed c on p.business_date=c.business_date order by business_date
""",
        (rs, row) ->
            new Daily(
                rs.getDate("business_date").toLocalDate(),
                rs.getLong("placed"),
                rs.getLong("completed"),
                rs.getBigDecimal("value"),
                rs.getLong("portions"),
                rs.getBigDecimal("savings")),
        Timestamp.from(start),
        Timestamp.from(end),
        Timestamp.from(start),
        Timestamp.from(end));
  }

  public List<TopStore> topStores(Instant start, Instant end) {
    return jdbc.query(
        """
        select u.public_id, m.store_name, count(*) completed, sum(o.quantity) portions,
          sum(case when o.payment_status='CONFIRMED' then o.total_amount else 0 end) value
        from customer_orders o join merchant_profiles m on m.id=o.merchant_profile_id
          join users u on u.id=m.user_id
        where o.status='COMPLETED' and o.finalized_at>=? and o.finalized_at<?
        group by u.public_id, m.store_name
        order by value desc, completed desc, u.public_id limit 10
        """,
        (rs, row) ->
            new TopStore(
                rs.getObject("public_id", UUID.class),
                rs.getString("store_name"),
                rs.getLong("completed"),
                rs.getLong("portions"),
                rs.getBigDecimal("value")),
        Timestamp.from(start),
        Timestamp.from(end));
  }
}
