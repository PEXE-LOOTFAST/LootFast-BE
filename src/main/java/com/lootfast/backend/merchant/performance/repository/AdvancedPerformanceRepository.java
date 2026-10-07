package com.lootfast.backend.merchant.performance.repository;

import com.lootfast.backend.common.response.PagedResponse;
import com.lootfast.backend.merchant.performance.dto.AdvancedPerformanceResponse.*;
import com.lootfast.backend.merchant.performance.service.ReportMath;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdvancedPerformanceRepository {
  private final JdbcTemplate jdbc;

  public AdvancedPerformanceRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Summary summary(long merchant, Instant start, Instant end) {
    return jdbc.queryForObject(
        """
with cohort as (
  select count(*) as placed,
    count(*) filter(where status='COMPLETED') as completed,
    count(*) filter(where status='CANCELLED') as customer_cancelled,
    count(*) filter(where status='MERCHANT_CANCELLED') as merchant_cancelled,
    count(*) filter(where status='NO_SHOW') as no_show,
    count(*) filter(where status='RESERVED') as pending
  from customer_orders where merchant_profile_id=? and created_at>=? and created_at<?
), finals as (
  select coalesce(sum(total_amount) filter(where payment_status='CONFIRMED'),0) as revenue,
    coalesce(sum(quantity),0) as portions
  from customer_orders where merchant_profile_id=? and status='COMPLETED' and finalized_at>=? and finalized_at<?
) select * from cohort cross join finals
""",
        (rs, n) -> {
          long total = rs.getLong("placed"),
              completed = rs.getLong("completed"),
              customer = rs.getLong("customer_cancelled"),
              merchantCancel = rs.getLong("merchant_cancelled"),
              noShow = rs.getLong("no_show");
          return new Summary(
              total,
              completed,
              customer,
              merchantCancel,
              noShow,
              rs.getLong("pending"),
              rs.getBigDecimal("revenue"),
              rs.getLong("portions"),
              ReportMath.rate(completed, total),
              ReportMath.rate(customer + merchantCancel, total),
              ReportMath.rate(noShow, total));
        },
        merchant,
        Timestamp.from(start),
        Timestamp.from(end),
        merchant,
        Timestamp.from(start),
        Timestamp.from(end));
  }

  private static final String ENTRIES =
      """
with entries as (
  select deal_id,deal_name,1 as placed,status,null::numeric as revenue
  from customer_orders where merchant_profile_id=? and created_at>=? and created_at<?
  union all
  select deal_id,deal_name,0 as placed,null as status,total_amount as revenue
  from customer_orders where merchant_profile_id=? and status='COMPLETED' and payment_status='CONFIRMED'
    and finalized_at>=? and finalized_at<?
)
""";

  public PagedResponse<DealPerformance> deals(
      long merchant, Instant start, Instant end, int page, int size) {
    var a = Timestamp.from(start);
    var b = Timestamp.from(end);
    Long total =
        jdbc.queryForObject(
            ENTRIES + "select count(*) from (select distinct deal_id from entries) t",
            Long.class,
            merchant,
            a,
            b,
            merchant,
            a,
            b);
    var rows =
        jdbc.query(
            ENTRIES
                + """
                  select d.public_id,max(e.deal_name) as name,sum(e.placed) as placed,
                    count(*) filter(where e.status='COMPLETED') as completed,
                    count(*) filter(where e.status='CANCELLED') as customer_cancelled,
                    count(*) filter(where e.status='MERCHANT_CANCELLED') as merchant_cancelled,
                    count(*) filter(where e.status='NO_SHOW') as no_show,
                    count(*) filter(where e.status='RESERVED') as pending,
                    coalesce(sum(e.revenue),0) as revenue
                  from entries e join deals d on d.id=e.deal_id
                  group by d.public_id order by revenue desc,d.public_id limit ? offset ?
                  """,
            (rs, n) -> {
              long count = rs.getLong("placed"),
                  completed = rs.getLong("completed"),
                  customer = rs.getLong("customer_cancelled"),
                  cancelled = rs.getLong("merchant_cancelled"),
                  noShow = rs.getLong("no_show");
              return new DealPerformance(
                  rs.getObject("public_id", UUID.class),
                  rs.getString("name"),
                  count,
                  completed,
                  customer,
                  cancelled,
                  noShow,
                  rs.getLong("pending"),
                  rs.getBigDecimal("revenue"),
                  ReportMath.rate(completed, count),
                  ReportMath.rate(customer + cancelled, count),
                  ReportMath.rate(noShow, count));
            },
            merchant,
            a,
            b,
            merchant,
            a,
            b,
            size,
            (long) page * size);
    return new PagedResponse<>(
        rows, page, size, total, total == 0 ? 0 : Math.toIntExact((total + size - 1) / size));
  }
}
