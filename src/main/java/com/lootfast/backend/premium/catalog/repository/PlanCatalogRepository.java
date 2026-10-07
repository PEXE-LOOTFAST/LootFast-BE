package com.lootfast.backend.premium.catalog.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lootfast.backend.common.exception.*;
import com.lootfast.backend.common.response.PagedResponse;
import com.lootfast.backend.premium.catalog.dto.*;
import com.lootfast.backend.premium.catalog.dto.PlanRequests.Configuration;
import com.lootfast.backend.premium.dto.PremiumBenefit;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class PlanCatalogRepository {
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  public PlanCatalogRepository(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  private static Instant instant(java.sql.ResultSet r, String key) throws java.sql.SQLException {
    var t = r.getTimestamp(key);
    return t == null ? null : t.toInstant();
  }

  private static final String SELECT =
      """
select r.*,p.code,p.audience,l.document_version terms_version,
  array(select b.benefit from premium_plan_benefits b where b.plan_revision_id=r.id order by b.benefit) benefit_codes
from premium_plan_revisions r join premium_plans p on p.id=r.plan_id
  join content_legal_versions l on l.id=r.terms_document_id
""";
  private static final RowMapper<PlanResponse> MAP =
      (r, n) -> {
        var benefits = EnumSet.noneOf(PremiumBenefit.class);
        var a = r.getArray("benefit_codes");
        try {
          for (var v : (Object[]) a.getArray()) benefits.add(PremiumBenefit.valueOf(v.toString()));
        } finally {
          a.free();
        }
        return new PlanResponse(
            r.getObject("id", UUID.class),
            r.getString("code"),
            PlanAudience.valueOf(r.getString("audience")),
            r.getInt("version"),
            r.getLong("revision"),
            r.getString("status"),
            r.getString("name"),
            r.getString("description"),
            r.getLong("price_vnd"),
            "VND",
            r.getInt("duration_days"),
            Set.copyOf(benefits),
            r.getObject("terms_document_id", UUID.class),
            r.getString("terms_version"),
            instant(r, "published_at"),
            instant(r, "updated_at"));
      };

  public UUID createPlan(String code, PlanAudience audience, UUID actor, Instant now) {
    UUID id = UUID.randomUUID();
    int n =
        jdbc.update(
            "insert into premium_plans(id,code,audience,created_at,created_by) values(?,?,?,?,?) on"
                + " conflict(code) do nothing",
            id,
            code,
            audience.name(),
            Timestamp.from(now),
            actor);
    if (n == 0) throw new ApiException(ErrorCode.PREMIUM_PLAN_CONFLICT, "Plan code already exists");
    return id;
  }

  public UUID lockPlan(String code) {
    return jdbc
        .query(
            "select id from premium_plans where code=? for update",
            (r, n) -> r.getObject("id", UUID.class),
            code)
        .stream()
        .findFirst()
        .orElseThrow(() -> new ApiException(ErrorCode.PREMIUM_PLAN_NOT_FOUND));
  }

  public String codeOf(UUID revision) {
    return jdbc
        .query(
            "select p.code from premium_plans p join premium_plan_revisions r on r.plan_id=p.id"
                + " where r.id=?",
            (r, n) -> r.getString(1),
            revision)
        .stream()
        .findFirst()
        .orElseThrow(() -> new ApiException(ErrorCode.PREMIUM_PLAN_NOT_FOUND));
  }

  public PlanAudience audience(UUID plan) {
    return PlanAudience.valueOf(
        jdbc.queryForObject("select audience from premium_plans where id=?", String.class, plan));
  }

  public Optional<PlanResponse> get(UUID id, boolean publishedOnly) {
    return jdbc
        .query(
            SELECT + " where r.id=?" + (publishedOnly ? " and r.status='PUBLISHED'" : ""), MAP, id)
        .stream()
        .findFirst();
  }

  public UUID draft(UUID plan, Configuration c, UUID actor, Instant now) {
    if (Boolean.TRUE.equals(
        jdbc.queryForObject(
            "select exists(select 1 from premium_plan_revisions where plan_id=? and"
                + " status='DRAFT')",
            Boolean.class,
            plan)))
      throw new ApiException(ErrorCode.PREMIUM_PLAN_CONFLICT, "This plan already has a draft");
    Integer version =
        jdbc.queryForObject(
            "select coalesce(max(version),0)+1 from premium_plan_revisions where plan_id=?",
            Integer.class,
            plan);
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
insert into premium_plan_revisions(id,plan_id,version,revision,status,name,description,price_vnd,duration_days,terms_document_id,updated_at,updated_by)
values(?,?,?,0,'DRAFT',?,?,?,?,?,?,?)
""",
        id,
        plan,
        version,
        c.name().trim(),
        c.description().trim(),
        c.priceVnd(),
        c.durationDays(),
        c.termsDocumentId(),
        Timestamp.from(now),
        actor);
    benefits(id, c.benefits());
    return id;
  }

  private void benefits(UUID id, Set<PremiumBenefit> values) {
    jdbc.update("delete from premium_plan_benefits where plan_revision_id=?", id);
    jdbc.batchUpdate(
        "insert into premium_plan_benefits(plan_revision_id,benefit) values(?,?)",
        values.stream().map(v -> new Object[] {id, v.name()}).toList());
  }

  public void edit(UUID id, Configuration c, UUID actor, Instant now) {
    jdbc.update(
        """
update premium_plan_revisions set name=?,description=?,price_vnd=?,duration_days=?,terms_document_id=?,revision=revision+1,updated_at=?,updated_by=? where id=? and status='DRAFT'
""",
        c.name().trim(),
        c.description().trim(),
        c.priceVnd(),
        c.durationDays(),
        c.termsDocumentId(),
        Timestamp.from(now),
        actor,
        id);
    benefits(id, c.benefits());
  }

  public Optional<PlanResponse> published(String code) {
    return jdbc.query(SELECT + " where p.code=? and r.status='PUBLISHED'", MAP, code).stream()
        .findFirst();
  }

  public void state(UUID id, String status, UUID actor, Instant now) {
    jdbc.update(
        """
        update premium_plan_revisions set status=?,revision=revision+1,
          published_at=case when ?='PUBLISHED' then cast(? as timestamptz) else published_at end,
          updated_at=?,updated_by=? where id=?
        """,
        status,
        status,
        Timestamp.from(now),
        Timestamp.from(now),
        actor,
        id);
  }

  public PagedResponse<PlanResponse> list(
      boolean admin, PlanAudience audience, String code, int page, int size) {
    String where =
        " where (? is null or p.audience=?) and (? is null or p.code=?)"
            + (admin ? "" : " and r.status='PUBLISHED'");
    // PostgreSQL needs a concrete type for a nullable parameter in IS NULL.
    where = where.replace("? is null", "cast(? as varchar) is null");
    Object a = audience == null ? null : audience.name();
    Long total =
        jdbc.queryForObject(
            "select count(*) from premium_plan_revisions r join premium_plans p on p.id=r.plan_id"
                + where,
            Long.class,
            a,
            a,
            code,
            code);
    var rows =
        jdbc.query(
            SELECT + where + " order by p.code,r.version desc limit ? offset ?",
            MAP,
            a,
            a,
            code,
            code,
            size,
            (long) page * size);
    return new PagedResponse<>(
        rows, page, size, total, total == 0 ? 0 : Math.toIntExact((total + size - 1) / size));
  }

  public void audit(PlanResponse r, UUID actor, String action, String reason, Instant now) {
    try {
      jdbc.update(
          "insert into"
              + " premium_plan_audits(id,plan_revision_id,actor_id,action,reason,snapshot,created_at)"
              + " values(?,?,?,?,?,cast(? as jsonb),?)",
          UUID.randomUUID(),
          r.id(),
          actor,
          action,
          reason,
          json.writeValueAsString(r),
          Timestamp.from(now));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Cannot serialize plan audit", e);
    }
  }

  public PagedResponse<PlanAuditResponse> history(String code, int page, int size) {
    Long total =
        jdbc.queryForObject(
            "select count(*) from premium_plan_audits a join premium_plan_revisions r on"
                + " r.id=a.plan_revision_id join premium_plans p on p.id=r.plan_id where p.code=?",
            Long.class,
            code);
    var rows =
        jdbc.query(
            """
select a.* from premium_plan_audits a join premium_plan_revisions r on r.id=a.plan_revision_id
  join premium_plans p on p.id=r.plan_id where p.code=? order by a.created_at desc,a.id limit ? offset ?
""",
            (r, n) -> {
              try {
                return new PlanAuditResponse(
                    r.getObject("id", UUID.class),
                    r.getObject("plan_revision_id", UUID.class),
                    r.getObject("actor_id", UUID.class),
                    r.getString("action"),
                    r.getString("reason"),
                    json.readValue(r.getString("snapshot"), PlanResponse.class),
                    instant(r, "created_at"));
              } catch (JsonProcessingException e) {
                throw new IllegalStateException("Cannot read plan audit", e);
              }
            },
            code,
            size,
            (long) page * size);
    return new PagedResponse<>(
        rows, page, size, total, total == 0 ? 0 : Math.toIntExact((total + size - 1) / size));
  }
}
