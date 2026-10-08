package com.lootfast.backend.premium.vnpay;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class VnpayReceiptRepository {
  private final JdbcTemplate jdbc;

  public VnpayReceiptRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public record Target(UUID order, long user, UUID publicUser) {}

  public Optional<Target> target(String reference) {
    return jdbc
        .query(
            "select o.id,o.user_id,u.public_id from premium_payment_attempts a join premium_orders"
                + " o on o.id=a.order_id join users u on u.id=o.user_id where a.reference=?",
            (r, n) ->
                new Target(
                    r.getObject("id", UUID.class),
                    r.getLong("user_id"),
                    r.getObject("public_id", UUID.class)),
            reference)
        .stream()
        .findFirst();
  }

  public Optional<String> outcome(UUID order, String fingerprint) {
    return jdbc
        .query(
            "select outcome from premium_payment_receipts where order_id=? and fingerprint=?",
            (r, n) -> r.getString(1),
            order,
            fingerprint)
        .stream()
        .findFirst();
  }

  public UUID receipt(UUID order, VnpayNotification v, Instant now, String source) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into"
            + " premium_payment_receipts(id,order_id,fingerprint,merchant_code,transaction_no,amount_scaled,response_code,transaction_status,bank_code,paid_at,received_at,outcome,source)"
            + " values(?,?,?,?,?,?,?,?,?,?,?,'WAITING',?)",
        id,
        order,
        v.fingerprint(),
        v.merchant(),
        v.transactionNo(),
        v.amount(),
        v.responseCode(),
        v.transactionStatus(),
        v.bankCode(),
        v.paidAt() == null ? null : Timestamp.from(v.paidAt()),
        Timestamp.from(now),
        source);
    return id;
  }

  public boolean claim(UUID order, VnpayNotification v, Instant now) {
    jdbc.update(
        "insert into"
            + " premium_provider_transactions(merchant_code,transaction_no,order_id,created_at)"
            + " values(?,?,?,?) on conflict(merchant_code,transaction_no) do nothing",
        v.merchant(),
        v.transactionNo(),
        order,
        Timestamp.from(now));
    return order.equals(
        jdbc.queryForObject(
            "select order_id from premium_provider_transactions where merchant_code=? and"
                + " transaction_no=?",
            UUID.class,
            v.merchant(),
            v.transactionNo()));
  }

  public Optional<String> fulfilledTransaction(UUID order) {
    return jdbc
        .query(
            "select transaction_no from premium_payment_receipts where order_id=? and"
                + " outcome='FULFILLED'",
            (r, n) -> r.getString(1),
            order)
        .stream()
        .findFirst();
  }

  public void finish(UUID receipt, String outcome) {
    jdbc.update("update premium_payment_receipts set outcome=? where id=?", outcome, receipt);
  }

  public void state(UUID order, String orderState, String attemptState) {
    jdbc.update("update premium_orders set status=? where id=?", orderState, order);
    jdbc.update(
        "update premium_payment_attempts set status=? where order_id=?", attemptState, order);
  }

  public void event(UUID order, UUID receipt, String type, Instant now) {
    jdbc.update(
        "insert into premium_order_events(id,order_id,event_type,created_at,receipt_id)"
            + " values(?,?,?,?,?)",
        UUID.randomUUID(),
        order,
        type,
        Timestamp.from(now),
        receipt);
  }

  public void link(UUID order, UUID period, UUID receipt) {
    int changed =
        jdbc.update(
            "update premium_subscription_periods set premium_order_id=? where id=? and"
                + " purchase_reference=? and (premium_order_id is null or premium_order_id=?)",
            order,
            period,
            order,
            order);
    if (changed != 1) throw new IllegalStateException("Subscription period did not match order");
    jdbc.update(
        "update premium_payment_receipts set period_id=?,outcome='FULFILLED' where id=?",
        period,
        receipt);
  }
}
