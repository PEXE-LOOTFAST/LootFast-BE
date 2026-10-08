package com.lootfast.backend.buyer.savedfilter.repository;

import com.lootfast.backend.buyer.savedfilter.dto.*;
import com.lootfast.backend.common.response.PagedResponse;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;

@Repository
public class SavedFilterRepository {
  private final JdbcTemplate jdbc;

  public SavedFilterRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static LocalTime time(ResultSet r, String c) throws SQLException {
    return r.getObject(c, LocalTime.class);
  }

  private static final RowMapper<SavedFilterResponse> MAPPER =
      (r, n) ->
          new SavedFilterResponse(
              r.getObject("id", UUID.class),
              new SavedFilterCriteria(
                  r.getString("name"),
                  r.getBigDecimal("latitude"),
                  r.getBigDecimal("longitude"),
                  r.getBigDecimal("radius_km"),
                  r.getString("keyword"),
                  r.getString("category_code"),
                  r.getBigDecimal("max_price"),
                  (Integer) r.getObject("min_discount_percent"),
                  time(r, "pickup_from"),
                  time(r, "pickup_to")),
              r.getLong("revision"),
              r.getTimestamp("created_at").toInstant(),
              r.getTimestamp("updated_at").toInstant(),
              r.getBoolean("notifications_enabled"));

  public long count(long user) {
    return jdbc.queryForObject(
        "select count(*) from buyer_saved_filters where user_id=?", Long.class, user);
  }

  public Optional<SavedFilterResponse> get(long user, UUID id) {
    return jdbc
        .query("select * from buyer_saved_filters where user_id=? and id=?", MAPPER, user, id)
        .stream()
        .findFirst();
  }

  public PagedResponse<SavedFilterResponse> list(long user, int page, int size) {
    long total = count(user);
    var items =
        jdbc.query(
            "select * from buyer_saved_filters where user_id=? order by created_at desc,id limit ?"
                + " offset ?",
            MAPPER,
            user,
            size,
            (long) page * size);
    return new PagedResponse<>(items, page, size, total, (int) ((total + size - 1) / size));
  }

  public void create(long user, UUID id, SavedFilterCriteria c, Instant now) {
    jdbc.update(
        """
insert into buyer_saved_filters(id,user_id,name,latitude,longitude,radius_km,keyword,category_code,
  max_price,min_discount_percent,pickup_from,pickup_to,created_at,updated_at,search_keyword)
values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
""",
        id,
        user,
        c.name(),
        c.latitude(),
        c.longitude(),
        c.radiusKm(),
        c.keyword(),
        c.categoryCode(),
        c.maxPrice(),
        c.minDiscountPercent(),
        c.pickupFrom(),
        c.pickupTo(),
        Timestamp.from(now),
        Timestamp.from(now),
        com.lootfast.backend.common.text.SearchNormalizer.normalize(c.keyword()));
  }

  public void update(long user, UUID id, SavedFilterCriteria c, Instant now) {
    jdbc.update(
        """
update buyer_saved_filters set name=?,latitude=?,longitude=?,radius_km=?,keyword=?,category_code=?,max_price=?,
    min_discount_percent=?,pickup_from=?,pickup_to=?,updated_at=?,search_keyword=?,notifications_enabled_at=case when notifications_enabled then cast(? as timestamptz) else null end,revision=revision+1 where user_id=? and id=?
""",
        c.name(),
        c.latitude(),
        c.longitude(),
        c.radiusKm(),
        c.keyword(),
        c.categoryCode(),
        c.maxPrice(),
        c.minDiscountPercent(),
        c.pickupFrom(),
        c.pickupTo(),
        Timestamp.from(now),
        com.lootfast.backend.common.text.SearchNormalizer.normalize(c.keyword()),
        Timestamp.from(now),
        user,
        id);
  }

  public void notifications(long user, UUID id, boolean enabled, Instant now) {
    jdbc.update(
        "update buyer_saved_filters set notifications_enabled=?,"
            + " notifications_enabled_at=?,updated_at=?,revision=revision+1 where user_id=? and"
            + " id=?",
        enabled,
        enabled ? Timestamp.from(now) : null,
        Timestamp.from(now),
        user,
        id);
  }

  public void delete(long user, UUID id) {
    jdbc.update("delete from buyer_saved_filters where user_id=? and id=?", user, id);
  }
}
