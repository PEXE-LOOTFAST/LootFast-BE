package com.lootfast.backend.merchant.performance.repository;

import com.lootfast.backend.merchant.performance.dto.MerchantPerformanceResponse.DailyPerformance;
import com.lootfast.backend.merchant.performance.dto.MerchantPerformanceResponse.TopDeal;
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
public class MerchantPerformanceRepository {
  private final JdbcTemplate jdbc;
  public MerchantPerformanceRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public Map<OrderStatus, Long> placedOrders(Long merchantId, Instant start, Instant end) {
    var result = new EnumMap<OrderStatus, Long>(OrderStatus.class);
    for (var status : OrderStatus.values()) result.put(status, 0L);
    jdbc.query("""
        select status, count(*) as total from customer_orders
        where merchant_profile_id = ? and created_at >= ? and created_at < ?
        group by status
        """, rs -> {
          result.put(OrderStatus.valueOf(rs.getString("status")), rs.getLong("total"));
        }, merchantId, Timestamp.from(start), Timestamp.from(end));
    return result;
  }

  public List<DailyPerformance> daily(Long merchantId, Instant start, Instant end) {
    return jdbc.query("""
        select cast(finalized_at at time zone 'Asia/Ho_Chi_Minh' as date) as business_date,
               sum(case when payment_status = 'CONFIRMED' then total_amount else 0 end) as revenue,
               sum(quantity) as portions,
               sum((original_unit_price - unit_price) * quantity) as savings
        from customer_orders
        where merchant_profile_id = ? and status = 'COMPLETED'
          and finalized_at >= ? and finalized_at < ?
        group by business_date order by business_date
        """, (rs, row) -> new DailyPerformance(rs.getDate("business_date").toLocalDate(),
            rs.getBigDecimal("revenue"), rs.getLong("portions"), rs.getBigDecimal("savings")),
        merchantId, Timestamp.from(start), Timestamp.from(end));
  }

  public List<TopDeal> topDeals(Long merchantId, Instant start, Instant end) {
    return jdbc.query("""
        select d.public_id, d.name, count(*) as completed_orders,
               sum(o.quantity) as portions, sum(o.total_amount) as revenue
        from customer_orders o join deals d on d.id = o.deal_id
        where o.merchant_profile_id = ? and o.status = 'COMPLETED'
          and o.payment_status = 'CONFIRMED' and o.finalized_at >= ? and o.finalized_at < ?
        group by d.public_id, d.name
        order by revenue desc, d.public_id limit 10
        """, (rs, row) -> new TopDeal(rs.getObject("public_id", UUID.class), rs.getString("name"),
            rs.getLong("completed_orders"), rs.getLong("portions"), rs.getBigDecimal("revenue")),
        merchantId, Timestamp.from(start), Timestamp.from(end));
  }
}
