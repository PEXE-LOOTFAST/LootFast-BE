package com.lootfast.backend.premium.checkout.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lootfast.backend.common.response.PagedResponse;
import com.lootfast.backend.premium.catalog.dto.*;
import com.lootfast.backend.premium.checkout.dto.CheckoutDtos.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CheckoutRepository {
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  public CheckoutRepository(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  private static final String SELECT =
      "select o.*,a.id attempt_id,a.reference,a.status attempt_status,a.created_at"
          + " attempt_created_at,a.expires_at attempt_expires_at from premium_orders o left join"
          + " premium_payment_attempts a on a.order_id=o.id";

  private Order map(java.sql.ResultSet r, int n) throws java.sql.SQLException {
    try {
      return new Order(
          r.getObject("id", UUID.class),
          r.getString("status"),
          json.readValue(r.getString("plan_snapshot"), PlanResponse.class),
          r.getTimestamp("accepted_at").toInstant(),
          r.getTimestamp("created_at").toInstant(),
          r.getTimestamp("expires_at").toInstant(),
          r.getObject("attempt_id") == null
              ? null
              : new Attempt(
                  r.getObject("attempt_id", UUID.class),
                  r.getString("reference"),
                  r.getString("attempt_status"),
                  r.getTimestamp("attempt_created_at").toInstant(),
                  r.getTimestamp("attempt_expires_at").toInstant()));
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException("Cannot read checkout snapshot", e);
    }
  }

  public Optional<Order> byKey(long user, UUID key) {
    return jdbc
        .query(SELECT + " where o.user_id=? and o.idempotency_key=?", this::map, user, key)
        .stream()
        .findFirst();
  }

  public Optional<Order> owned(long user, UUID id) {
    return jdbc.query(SELECT + " where o.user_id=? and o.id=?", this::map, user, id).stream()
        .findFirst();
  }

  public void lock(long user, UUID id) {
    jdbc.queryForList(
        "select id from premium_orders where user_id=? and id=? for update", user, id);
  }

  public boolean pending(long user, PlanAudience audience, Instant now) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "select exists(select 1 from premium_orders where user_id=? and audience=? and"
                + " status='PENDING_PAYMENT' and expires_at>?)",
            Boolean.class,
            user,
            audience.name(),
            Timestamp.from(now)));
  }

  public long recentCount(long user, Instant since) {
    return jdbc.queryForObject(
        "select count(*) from premium_orders where user_id=? and created_at>=?",
        Long.class,
        user,
        Timestamp.from(since));
  }

  public UUID insert(long user, UUID key, PlanResponse plan, Instant now, Instant expires) {
    UUID id = UUID.randomUUID();
    try {
      jdbc.update(
          "insert into"
              + " premium_orders(id,user_id,idempotency_key,plan_revision_id,audience,terms_document_id,plan_snapshot,status,accepted_at,created_at,expires_at)"
              + " values(?,?,?,?,?,?,cast(? as jsonb),'PENDING_PAYMENT',?,?,?)",
          id,
          user,
          key,
          plan.id(),
          plan.audience().name(),
          plan.termsDocumentId(),
          json.writeValueAsString(plan),
          Timestamp.from(now),
          Timestamp.from(now),
          Timestamp.from(expires));
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException("Cannot write checkout snapshot", e);
    }
    audit(id, "CREATED", now);
    return id;
  }

  public Attempt attempt(UUID order, String reference, Instant now, Instant expires, String url) {
    var id = UUID.randomUUID();
    jdbc.update(
        "insert into"
            + " premium_payment_attempts(id,order_id,reference,status,payment_url,created_at,expires_at)"
            + " values(?,?,?,'PENDING',?,?,?)",
        id,
        order,
        reference,
        url,
        Timestamp.from(now),
        Timestamp.from(expires));
    audit(order, "PAYMENT_INITIATED", now);
    return new Attempt(id, reference, "PENDING", now, expires);
  }

  public String paymentUrl(UUID attempt) {
    return jdbc.queryForObject(
        "select payment_url from premium_payment_attempts where id=?", String.class, attempt);
  }

  private void audit(UUID order, String event, Instant now) {
    jdbc.update(
        "insert into premium_order_events(id,order_id,event_type,created_at) values(?,?,?,?)",
        UUID.randomUUID(),
        order,
        event,
        Timestamp.from(now));
  }

  public PagedResponse<Order> list(long user, int page, int size) {
    var total =
        jdbc.queryForObject(
            "select count(*) from premium_orders where user_id=?", Long.class, user);
    var items =
        jdbc.query(
            SELECT + " where o.user_id=? order by o.created_at desc,o.id limit ? offset ?",
            this::map,
            user,
            size,
            (long) page * size);
    return new PagedResponse<>(
        items, page, size, total, total == 0 ? 0 : Math.toIntExact((total + size - 1) / size));
  }
}
