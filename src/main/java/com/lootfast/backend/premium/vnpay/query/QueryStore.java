package com.lootfast.backend.premium.vnpay.query;

import com.lootfast.backend.common.exception.*;
import com.lootfast.backend.premium.checkout.repository.CheckoutRepository;
import com.lootfast.backend.premium.vnpay.VnpayIpnService;
import com.lootfast.backend.user.entity.UserStatus;
import com.lootfast.backend.user.repository.UserRepository;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QueryStore {
  private final JdbcTemplate jdbc;
  private final Clock clock;
  private final VnpayIpnService payments;
  private final UserRepository users;
  private final CheckoutRepository orders;

  public QueryStore(
      JdbcTemplate jdbc,
      Clock clock,
      VnpayIpnService payments,
      UserRepository users,
      CheckoutRepository orders) {
    this.jdbc = jdbc;
    this.clock = clock;
    this.payments = payments;
    this.users = users;
    this.orders = orders;
  }

  Timestamp now() {
    return Timestamp.from(clock.instant());
  }

  public record Status(
      UUID orderId, String state, int attempts, Instant nextAttemptAt, String lastResult) {}

  @Transactional(timeout = 5)
  public Status request(String subject, UUID id) {
    long userId;
    try {
      userId = Long.parseLong(subject);
    } catch (RuntimeException e) {
      throw new ApiException(ErrorCode.ACCESS_DENIED);
    }
    var user =
        users
            .findByIdWithRoles(userId)
            .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    if (user.isLocked()) throw new ApiException(ErrorCode.ACCOUNT_LOCKED);
    if (user.getStatus() != UserStatus.VERIFIED)
      throw new ApiException(ErrorCode.ACCOUNT_NOT_VERIFIED);
    var order =
        orders
            .owned(userId, id)
            .orElseThrow(() -> new ApiException(ErrorCode.PREMIUM_ORDER_NOT_FOUND));
    if (order.attempt() == null)
      throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT, "No VNPay payment attempt exists");
    if (!order.status().equals("PENDING_PAYMENT"))
      throw new ApiException(
          ErrorCode.PAYMENT_STATE_CONFLICT, "This order needs no automatic reconciliation");
    var n = now();
    int updated =
        jdbc.update(
            "update premium_reconciliation_jobs set next_at=?,last_request_at=? where order_id=?"
                + " and state='WAITING' and attempts<12 and deadline>? and (last_claim_at is null"
                + " or last_claim_at<=?) and (last_claim_at is null or next_at<=?) and"
                + " (last_request_at is null or last_request_at<=?) and exists(select 1 from"
                + " premium_payment_attempts where order_id=? and created_at<=?)",
            n,
            n,
            id,
            n,
            Timestamp.from(clock.instant().minusSeconds(300)),
            n,
            Timestamp.from(clock.instant().minusSeconds(300)),
            id,
            Timestamp.from(clock.instant().minusSeconds(300)));
    if (updated != 1)
      throw new ApiException(
          ErrorCode.PAYMENT_RETRY_LATER,
          "Wait at least five minutes between checks; exhausted/review cases need support");
    return status(id);
  }

  @Transactional(readOnly = true, timeout = 5)
  public Status ownedStatus(String subject, UUID id) {
    long user;
    try {
      user = Long.parseLong(subject);
    } catch (RuntimeException e) {
      throw new ApiException(ErrorCode.ACCESS_DENIED);
    }
    var order =
        orders
            .owned(user, id)
            .orElseThrow(() -> new ApiException(ErrorCode.PREMIUM_ORDER_NOT_FOUND));
    if (order.attempt() == null)
      throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT, "No payment attempt exists");
    return status(id);
  }

  public Status status(UUID id) {
    return jdbc.queryForObject(
        "select * from premium_reconciliation_jobs where order_id=?",
        (r, i) ->
            new Status(
                id,
                r.getString("state"),
                r.getInt("attempts"),
                r.getTimestamp("next_at").toInstant(),
                r.getString("last_result")),
        id);
  }

  @Transactional(timeout = 5)
  public Optional<QueryJob> claim() {
    var n = now();
    // Final IPN outcomes stop unnecessary provider queries; no payment state is changed here.
    jdbc.update(
        "update premium_reconciliation_jobs j set"
            + " state='DONE',lease_token=null,lease_until=null,last_result='IPN_TERMINAL' from"
            + " premium_orders o where j.order_id=o.id and o.status in"
            + " ('FULFILLED','FAILED','CANCELLED','REVIEW_REQUIRED') and j.state in"
            + " ('WAITING','PROCESSING')");
    jdbc.update(
        "update premium_reconciliation_jobs set"
            + " state='EXHAUSTED',lease_token=null,lease_until=null,last_result='RETRY_LIMIT' where"
            + " state in ('WAITING','PROCESSING') and (lease_until is null or lease_until<=?) and"
            + " (attempts>=12 or deadline<=?)",
        n,
        n);
    UUID token = UUID.randomUUID();
    var rows =
        jdbc.query(
            "with due as (select order_id from premium_reconciliation_jobs where state in"
                + " ('WAITING','PROCESSING') and next_at<=? and (lease_until is null or"
                + " lease_until<=?) and attempts<12 and deadline>? order by next_at,order_id for"
                + " update skip locked limit 1) update premium_reconciliation_jobs j set"
                + " state='PROCESSING',attempts=attempts+1,lease_token=?,lease_until=?,last_claim_at=?,next_at=?"
                + " from due where j.order_id=due.order_id returning j.order_id,j.attempts",
            (r, i) -> new Object[] {r.getObject(1, UUID.class), r.getInt(2)},
            n,
            n,
            n,
            token,
            Timestamp.from(clock.instant().plusSeconds(60)),
            n,
            Timestamp.from(clock.instant().plusSeconds(300)));
    if (rows.isEmpty()) return Optional.empty();
    UUID order = (UUID) rows.getFirst()[0];
    int attempt = (Integer) rows.getFirst()[1];
    var job =
        jdbc.queryForObject(
            "select reference,created_at from premium_payment_attempts where order_id=?",
            (r, i) ->
                new QueryJob(order, token, r.getString(1), r.getTimestamp(2).toInstant(), attempt),
            order);
    jdbc.update(
        "insert into premium_query_runs(request_id,order_id,started_at) values(?,?,?)",
        token,
        order,
        n);
    return Optional.of(job);
  }

  @Transactional(timeout = 5)
  public String finish(QueryJob job, QueryResult response) {
    var lease =
        jdbc.query(
            "select lease_token,lease_until from premium_reconciliation_jobs where order_id=? for"
                + " update",
            (r, i) -> new Object[] {r.getObject(1, UUID.class), r.getTimestamp(2)},
            job.orderId());
    if (lease.isEmpty()
        || !job.token().equals(lease.getFirst()[0])
        || !((Timestamp) lease.getFirst()[1]).toInstant().isAfter(clock.instant())) {
      jdbc.update(
          "update premium_query_runs set completed_at=?,result='STALE_WORKER' where request_id=?"
              + " and completed_at is null",
          now(),
          job.token());
      return "STALE_WORKER";
    }
    String result = response.result(), state = "WAITING";
    if (response.notification() != null) {
      String ack = payments.processQuery(response.notification()).code();
      String orderState =
          jdbc.queryForObject(
              "select status from premium_orders where id=?", String.class, job.orderId());
      if (orderState.equals("FULFILLED") || orderState.equals("FAILED")) {
        state = "DONE";
        result = orderState;
      } else if (orderState.equals("REVIEW_REQUIRED") || ack.equals("04") || ack.equals("01")) {
        state = "MANUAL_REVIEW";
        result = "REVIEW_REQUIRED";
      } else result = "PENDING";
    } else if (response.result().equals("NON_PAYMENT")
        || (response.result().equals("PROVIDER_ERROR")
            && Set.of("02", "03", "97").contains(response.code()))) state = "MANUAL_REVIEW";
    if (state.equals("WAITING") && job.attempt() >= 12) state = "EXHAUSTED";
    long delay = Math.min(3600, 300L << Math.min(job.attempt() - 1, 4));
    jdbc.update(
        "update premium_reconciliation_jobs set"
            + " state=?,next_at=?,lease_token=null,lease_until=null,last_result=? where order_id=?"
            + " and lease_token=?",
        state,
        Timestamp.from(clock.instant().plusSeconds(delay)),
        result,
        job.orderId(),
        job.token());
    jdbc.update(
        "update premium_query_runs set completed_at=?,result=?,provider_code=?,response_id=? where"
            + " request_id=?",
        now(),
        result,
        response.code(),
        response.responseId(),
        job.token());
    return result;
  }
}
