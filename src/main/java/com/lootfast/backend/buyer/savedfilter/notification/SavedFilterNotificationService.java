package com.lootfast.backend.buyer.savedfilter.notification;

import com.lootfast.backend.deal.entity.Deal;
import com.lootfast.backend.deal.repository.DealRepository;
import com.lootfast.backend.notification.outbox.EmailOutboxService;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.util.HtmlUtils;

@Service
public class SavedFilterNotificationService {
  private static final int BATCH_SIZE = 100;
  private final JdbcTemplate jdbc;
  private final DealRepository deals;
  private final SavedFilterRecipientRepository recipients;
  private final EmailOutboxService emails;
  private final Clock clock;

  public SavedFilterNotificationService(
      JdbcTemplate jdbc,
      DealRepository deals,
      SavedFilterRecipientRepository recipients,
      EmailOutboxService emails,
      Clock clock) {
    this.jdbc = jdbc;
    this.deals = deals;
    this.recipients = recipients;
    this.emails = emails;
    this.clock = clock;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void published(Deal deal) {
    Instant now = clock.instant();
    Instant eventAt = now.isAfter(deal.getAvailableFrom()) ? now : deal.getAvailableFrom();
    jdbc.update(
        """
insert into premium_deal_notification_jobs(deal_id,event_at,next_attempt_at) values (?,?,?) on conflict do nothing
""",
        deal.getId(),
        Timestamp.from(eventAt),
        Timestamp.from(eventAt));
  }

  @Transactional(timeout = 5)
  public boolean processBatch() {
    Instant now = clock.instant();
    var jobs =
        jdbc.query(
            """
select deal_id,event_at,last_user_id from premium_deal_notification_jobs
where completed_at is null and failed_at is null and next_attempt_at<=? order by next_attempt_at,deal_id
limit 1 for update skip locked
""",
            (r, n) ->
                new Job(
                    r.getLong("deal_id"),
                    r.getTimestamp("event_at").toInstant(),
                    r.getLong("last_user_id")),
            Timestamp.from(now));
    if (jobs.isEmpty()) return false;
    var job = jobs.getFirst();
    try {
      return process(job, now);
    } catch (RuntimeException e) {
      throw new BatchFailure(job.deal(), job.after(), e);
    }
  }

  private boolean process(Job job, Instant now) {
    var deal = deals.findById(job.deal()).orElseThrow();
    if (!deal.getAvailableUntil().isAfter(now) || !deal.getPickupEnd().isAfter(now)) {
      done(job, now);
      return true;
    }
    if (deal.getStatus() != com.lootfast.backend.deal.entity.DealStatus.ACTIVE
        || deal.getRemainingQuantity() == 0
        || deal.getMerchant().getUser().isLocked()
        || (deal.getMerchant().getSuspendedUntil() != null
            && deal.getMerchant().getSuspendedUntil().isAfter(now))) {
      jdbc.update(
          "update premium_deal_notification_jobs set next_attempt_at=? where deal_id=?",
          Timestamp.from(now.plusSeconds(30)),
          job.deal());
      return true;
    }
    var candidates = recipients.candidates(job.after(), now, BATCH_SIZE);
    if (candidates.isEmpty()) {
      done(job, now);
      return true;
    }
    recipients.lockRecipients(candidates, deal.getMerchant().getUser().getId());
    var ids =
        recipients.recipients(
            job.deal(),
            job.after(),
            candidates.getLast(),
            job.eventAt(),
            clock.instant(),
            BATCH_SIZE);
    String title = "Deal phù hợp bộ lọc của bạn";
    String message =
        "Deal %s tại %s phù hợp bộ lọc đã lưu của bạn."
            .formatted(deal.getName(), deal.getMerchant().getStoreName());
    var notificationIds = new ArrayList<UUID>();
    var args = new ArrayList<Object[]>();
    for (Long id : ids) {
      UUID notificationId = UUID.randomUUID();
      notificationIds.add(notificationId);
      args.add(
          new Object[] {
            notificationId,
            id,
            deal.getPublicId(),
            deal.getMerchant().getUser().getPublicId(),
            title,
            message,
            deal.getPrimaryImageUrl(),
            Timestamp.from(now)
          });
    }
    jdbc.batchUpdate(
        """
insert into notifications(public_id,recipient_user_id,type,match_reason,deal_public_id,store_public_id,title,message,image_url,created_at)
values (?,?,'MATCHING_DEAL','SAVED_FILTER',?,?,?,?,?,?)
on conflict(recipient_user_id,type,deal_public_id) where type='MATCHING_DEAL' do nothing
""",
        args);
    String body =
        "<h2>"
            + HtmlUtils.htmlEscape(title)
            + "</h2><p>"
            + HtmlUtils.htmlEscape(message)
            + "</p><p>Giá hiện tại: "
            + deal.getDiscountedPrice().toPlainString()
            + " VND. Mở LootFast để xem thời gian nhận món và số lượng còn lại.</p>";
    emails.enqueueFilterBatch(notificationIds, "LootFast — " + title, body);
    jdbc.update(
        "update premium_deal_notification_jobs set"
            + " last_user_id=?,completed_at=?,failure_count=0,last_error_code=null where deal_id=?",
        candidates.getLast(),
        candidates.size() < BATCH_SIZE ? Timestamp.from(now) : null,
        job.deal());
    return true;
  }

  private void done(Job j, Instant now) {
    jdbc.update(
        "update premium_deal_notification_jobs set"
            + " completed_at=?,failure_count=0,last_error_code=null where deal_id=?",
        Timestamp.from(now),
        j.deal());
  }

  public static final class BatchFailure extends RuntimeException {
    private final long dealId, after;

    public BatchFailure(long dealId, long after, RuntimeException cause) {
      super("Saved filter notification batch failed", cause);
      this.dealId = dealId;
      this.after = after;
    }

    public long dealId() {
      return dealId;
    }
  }

  // Runs only after the failed transaction rolls back; cursor fencing avoids delaying newer
  // progress.
  @Transactional(timeout = 5)
  public void defer(BatchFailure failure) {
    Instant now = clock.instant();
    String code = failure.getCause().getClass().getSimpleName();
    if (code.length() > 64) code = code.substring(0, 64);
    jdbc.update(
        """
update premium_deal_notification_jobs set
  next_attempt_at=cast(? as timestamptz) + (case failure_count when 0 then 30 when 1 then 120 when 2 then 600 else 1800 end)*interval '1 second',
  failure_count=failure_count+1,failed_at=case when failure_count>=4 then cast(? as timestamptz) else null end,last_error_code=?
where deal_id=? and last_user_id=? and completed_at is null and failed_at is null
""",
        Timestamp.from(now),
        Timestamp.from(now),
        code,
        failure.dealId,
        failure.after);
  }

  private record Job(long deal, Instant eventAt, long after) {}
}
