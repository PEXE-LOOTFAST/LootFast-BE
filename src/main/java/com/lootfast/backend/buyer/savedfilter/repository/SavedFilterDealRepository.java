package com.lootfast.backend.buyer.savedfilter.repository;

import com.lootfast.backend.buyer.savedfilter.dto.SavedFilterCriteria;
import com.lootfast.backend.common.response.PagedResponse;
import com.lootfast.backend.common.text.SearchNormalizer;
import com.lootfast.backend.deal.dto.DealFeedItemResponse;
import java.math.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

@Repository
public class SavedFilterDealRepository {
  private final NamedParameterJdbcTemplate jdbc;

  public SavedFilterDealRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static final String MATCHES =
      """
      with located as (
        select d.*,m.store_name,m.address_line,m.ward,m.district,m.city,
               u.public_id as merchant_public_id,
               (m.verified_at is not null) as verified,
               6371.0088*2*asin(sqrt(least(1.0,greatest(0.0,
                 power(sin(radians(m.latitude::double precision-:latitude)/2),2)
                 +cos(radians(:latitude))*cos(radians(m.latitude::double precision))
                  *power(sin(radians(m.longitude::double precision-:longitude)/2),2)
               )))) as distance_km
        from deals d join merchant_profiles m on m.id=d.merchant_profile_id
             join users u on u.id=m.user_id
        where d.status='ACTIVE' and d.remaining_quantity>0 and d.available_from<=:now
          and d.available_until>:now and d.pickup_end>:now and u.locked_at is null
          and m.store_name is not null and m.latitude is not null and m.longitude is not null
          and (m.suspended_until is null or m.suspended_until<=:now)
          and m.latitude between :minLatitude and :maxLatitude
      ), matching as (select * from located where distance_km<=:radius
      """;

  public PagedResponse<DealFeedItemResponse> search(
      SavedFilterCriteria c, LocalDate date, Instant now, int page, int size) {
    double lat = c.latitude().doubleValue();
    double delta = Math.toDegrees(c.radiusKm().doubleValue() / 6371.0088);
    var p =
        new MapSqlParameterSource()
            .addValue("latitude", lat)
            .addValue("longitude", c.longitude().doubleValue())
            .addValue("radius", c.radiusKm().doubleValue())
            .addValue("minLatitude", Math.max(-90, lat - delta))
            .addValue("maxLatitude", Math.min(90, lat + delta))
            .addValue("now", Timestamp.from(now))
            .addValue("size", size)
            .addValue("offset", (long) page * size);
    String sql = MATCHES;
    if (c.categoryCode() != null) {
      sql += " and category_code=:category";
      p.addValue("category", c.categoryCode());
    }
    if (c.maxPrice() != null) {
      sql += " and discounted_price<=:price";
      p.addValue("price", c.maxPrice());
    }
    if (c.minDiscountPercent() != null) {
      sql += " and (original_price-discounted_price)*100>=original_price*:discount";
      p.addValue("discount", c.minDiscountPercent());
    }
    if (c.keyword() != null) {
      sql +=
          " and (search_name like :keyword escape '!' or exists(select 1 from merchant_profiles mp"
              + " where mp.id=merchant_profile_id and mp.search_name like :keyword escape '!'))";
      String term =
          SearchNormalizer.normalize(c.keyword())
              .replace("!", "!!")
              .replace("%", "!%")
              .replace("_", "!_");
      p.addValue("keyword", "%" + term + "%");
    }
    ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
    Instant from =
        c.pickupFrom() == null
            ? date.atStartOfDay(zone).toInstant()
            : date.atTime(c.pickupFrom()).atZone(zone).toInstant();
    Instant to =
        c.pickupTo() == null
            ? date.plusDays(1).atStartOfDay(zone).toInstant()
            : date.atTime(c.pickupTo()).atZone(zone).toInstant();
    sql += " and pickup_end>:pickupFrom and pickup_start<:pickupTo) ";
    p.addValue("pickupFrom", Timestamp.from(from)).addValue("pickupTo", Timestamp.from(to));
    long total = jdbc.queryForObject(sql + "select count(*) from matching", p, Long.class);
    var rows =
        jdbc.query(
            sql
                + "select * from matching order by distance_km,public_id limit :size offset"
                + " :offset",
            p,
            (r, n) -> {
              var original = r.getBigDecimal("original_price");
              var price = r.getBigDecimal("discounted_price");
              return new DealFeedItemResponse(
                  r.getObject("public_id", UUID.class),
                  r.getString("name"),
                  r.getString("category_code"),
                  original,
                  price,
                  original
                      .subtract(price)
                      .multiply(BigDecimal.valueOf(100))
                      .divide(original, 0, RoundingMode.HALF_UP)
                      .intValueExact(),
                  r.getInt("remaining_quantity"),
                  r.getString("primary_image_url"),
                  r.getInt("preparation_minutes"),
                  r.getTimestamp("pickup_start").toInstant(),
                  r.getTimestamp("pickup_end").toInstant(),
                  BigDecimal.valueOf(r.getDouble("distance_km")).setScale(3, RoundingMode.HALF_UP),
                  new DealFeedItemResponse.MerchantSummary(
                      r.getObject("merchant_public_id", UUID.class),
                      r.getString("store_name"),
                      r.getString("address_line"),
                      r.getString("ward"),
                      r.getString("district"),
                      r.getString("city"),
                      r.getBoolean("verified")));
            });
    return new PagedResponse<>(rows, page, size, total, Math.toIntExact((total + size - 1) / size));
  }
}
