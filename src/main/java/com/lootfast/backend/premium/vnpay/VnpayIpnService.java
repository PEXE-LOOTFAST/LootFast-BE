package com.lootfast.backend.premium.vnpay;

import com.lootfast.backend.premium.checkout.repository.CheckoutRepository;
import com.lootfast.backend.premium.subscription.service.SubscriptionService;
import com.lootfast.backend.user.repository.UserRepository;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VnpayIpnService {
  private final VnpayReceiptRepository receipts;
  private final CheckoutRepository orders;
  private final SubscriptionService subscriptions;
  private final UserRepository users;
  private final Clock clock;

  public VnpayIpnService(
      VnpayReceiptRepository receipts,
      CheckoutRepository orders,
      SubscriptionService subscriptions,
      UserRepository users,
      Clock clock) {
    this.receipts = receipts;
    this.orders = orders;
    this.subscriptions = subscriptions;
    this.users = users;
    this.clock = clock;
  }

  @Transactional(timeout = 5)
  public VnpayAck process(VnpayNotification v) {
    return process(v, "IPN");
  }

  @Transactional(timeout = 5)
  public VnpayAck processQuery(VnpayNotification v) {
    return process(v, "QUERYDR");
  }

  private VnpayAck process(VnpayNotification v, String source) {
    var target = receipts.target(v.reference());
    if (target.isEmpty()) return VnpayAck.of("01");
    var t = target.get();
    var user = users.findByIdForUpdate(t.user()).orElseThrow();
    orders.lock(t.user(), t.order());
    var o = orders.owned(t.user(), t.order()).orElseThrow();
    var previous = receipts.outcome(o.id(), v.fingerprint());
    if (previous.isPresent())
      return VnpayAck.of(
          previous.get().equals("AMOUNT_MISMATCH")
              ? "04"
              : previous.get().equals("WAITING") ? "99" : "02");
    var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
    UUID receipt = receipts.receipt(o.id(), v, now, source);
    if (v.amount() != Math.multiplyExact(o.plan().priceVnd(), 100L)) {
      receipts.finish(receipt, "AMOUNT_MISMATCH");
      receipts.event(o.id(), receipt, "AMOUNT_MISMATCH", now);
      return VnpayAck.of("04");
    }
    if (!v.transactionNo().equals("0") && !receipts.claim(o.id(), v, now)) {
      review(o.id(), receipt, now, !o.status().equals("FULFILLED"));
      return VnpayAck.of("00");
    }
    if (v.successful()) {
      if (o.status().equals("FULFILLED")) {
        var txn = receipts.fulfilledTransaction(o.id());
        if (txn.isPresent() && txn.get().equals(v.transactionNo())) {
          receipts.finish(receipt, "IGNORED");
          receipts.event(o.id(), receipt, "CALLBACK_IGNORED", now);
          return VnpayAck.of("02");
        }
        review(o.id(), receipt, now, false);
        return VnpayAck.of("00");
      }
      if (o.status().equals("REVIEW_REQUIRED")
          || !user.hasRole(o.plan().audience().role())
          || v.paidAt() != null
              && (v.paidAt().isBefore(o.attempt().createdAt().truncatedTo(ChronoUnit.SECONDS))
                  || v.paidAt().isAfter(now.plusSeconds(300)))) {
        review(o.id(), receipt, now, true);
        return VnpayAck.of("00");
      }
      var period = subscriptions.applyConfirmedPurchase(t.publicUser(), o.id(), o.plan().id(), now);
      if (!period.planRevisionId().equals(o.plan().id())
          || period.priceVnd() != o.plan().priceVnd()
          || period.durationDays() != o.plan().durationDays()
          || !period.benefits().equals(o.plan().benefits())
          || !period.termsDocumentId().equals(o.plan().termsDocumentId()))
        throw new IllegalStateException("Order snapshot does not match period");
      receipts.link(o.id(), period.id(), receipt);
      receipts.finish(receipt, "FULFILLED");
      receipts.state(o.id(), "FULFILLED", "SUCCEEDED");
      receipts.event(o.id(), receipt, "FULFILLED", now);
      return VnpayAck.of("00");
    }
    if (o.status().equals("FULFILLED")) {
      if (java.util.Set.of("04", "05", "06", "07", "09").contains(v.transactionStatus())
          || v.responseCode().equals("07")) {
        review(o.id(), receipt, now, false);
        return VnpayAck.of("00");
      }
      receipts.finish(receipt, "IGNORED");
      receipts.event(o.id(), receipt, "CALLBACK_IGNORED", now);
      return VnpayAck.of("02");
    }
    if (v.transactionStatus().equals("01")) {
      return VnpayAck.of("99");
    }
    if (!v.transactionStatus().equals("02")
        || (source.equals("IPN") && v.responseCode().equals("00"))
        || v.responseCode().equals("07")
        || o.status().equals("REVIEW_REQUIRED")) {
      review(o.id(), receipt, now, true);
      return VnpayAck.of("00");
    }
    receipts.finish(receipt, "FAILED");
    receipts.state(o.id(), "FAILED", "FAILED");
    receipts.event(o.id(), receipt, "PAYMENT_FAILED", now);
    return VnpayAck.of("00");
  }

  private void review(UUID order, UUID receipt, Instant now, boolean updateState) {
    receipts.finish(receipt, "REVIEW_REQUIRED");
    if (updateState) receipts.state(order, "REVIEW_REQUIRED", "REVIEW_REQUIRED");
    receipts.event(order, receipt, "REVIEW_REQUIRED", now);
  }

  @Transactional(readOnly = true, timeout = 5)
  public UUID returnOrder(VnpayNotification v) {
    var t = receipts.target(v.reference()).orElseThrow(() -> new InvalidVnpayNotification("01"));
    var o = orders.owned(t.user(), t.order()).orElseThrow();
    if (v.amount() != Math.multiplyExact(o.plan().priceVnd(), 100L))
      throw new InvalidVnpayNotification("04");
    return o.id();
  }
}
