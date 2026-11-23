package com.lootfast.backend.notification.service;

import com.lootfast.backend.deal.entity.Deal;
import com.lootfast.backend.deal.entity.DealStatus;
import com.lootfast.backend.deal.repository.DealRepository;
import com.lootfast.backend.notification.dto.NotificationResponse;
import com.lootfast.backend.notification.entity.DealMatchReason;
import com.lootfast.backend.notification.entity.NotificationType;
import com.lootfast.backend.notification.event.MatchingDealEmailEvent;
import com.lootfast.backend.notification.event.NotificationCreatedEvent;
import com.lootfast.backend.notification.matching.DealMatchJob;
import com.lootfast.backend.notification.matching.DealMatchJobRepository;
import com.lootfast.backend.notification.matching.DealMatchScorer;
import com.lootfast.backend.notification.matching.MatchingProperties;
import com.lootfast.backend.notification.repository.MatchingDealRecipient;
import com.lootfast.backend.notification.repository.MatchingDealRecipientRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Preference-based "matching deal" notifications.
 *
 * <p>Publishing a deal only enqueues a {@link DealMatchJob} in the merchant's transaction, so the
 * publish API stays fast regardless of how many buyers exist. A background worker
 * ({@code DealMatchScheduler}) then calls {@link #processNextJob()}, which handles one keyset page
 * of buyers per short transaction: it scores each buyer's preference signals, applies the daily
 * cap, inserts notifications idempotently and publishes realtime/email events after commit.
 */
@Service
public class MatchingDealNotificationService {

    private static final String TITLE = "Deal phù hợp dành cho bạn";
    private static final Duration[] BACKOFF = {
            Duration.ofSeconds(30), Duration.ofMinutes(2), Duration.ofMinutes(10), Duration.ofMinutes(30)
    };

    private final MatchingDealRecipientRepository recipientRepository;
    private final DealMatchJobRepository jobRepository;
    private final DealRepository dealRepository;
    private final DealMatchScorer scorer;
    private final MatchingProperties properties;
    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public MatchingDealNotificationService(
            MatchingDealRecipientRepository recipientRepository,
            DealMatchJobRepository jobRepository,
            DealRepository dealRepository,
            DealMatchScorer scorer,
            MatchingProperties properties,
            JdbcTemplate jdbc,
            ApplicationEventPublisher eventPublisher,
            Clock clock
    ) {
        this.recipientRepository = recipientRepository;
        this.jobRepository = jobRepository;
        this.dealRepository = dealRepository;
        this.scorer = scorer;
        this.properties = properties;
        this.jdbc = jdbc;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /** Called in the publish transaction: the job exists if and only if the publish commits. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(Deal deal) {
        if (!jobRepository.existsById(deal.getId())) {
            jobRepository.save(new DealMatchJob(deal.getId(), clock.instant()));
        }
    }

    /** Processes one batch of the oldest due job; returns false when there is nothing to do. */
    @Transactional(timeout = 30)
    public boolean processNextJob() {
        Instant now = clock.instant();
        DealMatchJob job = jobRepository.lockNextDue(now).orElse(null);
        if (job == null) {
            return false;
        }
        try {
            processBatch(job, now);
        } catch (RuntimeException exception) {
            throw new BatchFailure(job.getDealId(), job.getLastUserId(), exception);
        }
        return true;
    }

    /** Runs in a new transaction after the failed batch rolled back. */
    @Transactional(timeout = 5)
    public void recordFailure(BatchFailure failure) {
        Instant now = clock.instant();
        jobRepository.findById(failure.dealId()).ifPresent(job -> {
            Duration delay = BACKOFF[Math.min(job.getFailureCount(), BACKOFF.length - 1)];
            String code = failure.getCause().getClass().getSimpleName();
            jobRepository.recordFailure(
                    failure.dealId(),
                    failure.lastUserId(),
                    now,
                    now.plus(delay),
                    DealMatchJob.MAX_FAILURES,
                    code.length() > 64 ? code.substring(0, 64) : code
            );
        });
    }

    private void processBatch(DealMatchJob job, Instant now) {
        Deal deal = dealRepository.findById(job.getDealId()).orElse(null);
        if (deal == null || !stillWorthNotifying(deal, now)) {
            job.complete(now);
            return;
        }

        Instant dayStart = LocalDate.ofInstant(now, properties.zone())
                .atStartOfDay(properties.zone())
                .toInstant();
        List<MatchingDealRecipient> candidates = recipientRepository.findCandidates(
                deal.getId(),
                job.getLastUserId(),
                now.minus(properties.orderHistoryWindow()),
                properties.orderHistoryMinOrders(),
                dayStart,
                properties.batchSize()
        );
        if (candidates.isEmpty()) {
            job.complete(now);
            return;
        }

        notifyMatches(deal, candidates, now);
        job.advance(candidates.getLast().getUserId());
        if (candidates.size() < properties.batchSize()) {
            job.complete(now);
        }
    }

    private boolean stillWorthNotifying(Deal deal, Instant now) {
        var merchant = deal.getMerchant();
        return deal.getStatus() == DealStatus.ACTIVE
                && deal.getRemainingQuantity() > 0
                && deal.getAvailableUntil().isAfter(now)
                && !merchant.getUser().isLocked()
                && (merchant.getSuspendedUntil() == null || !merchant.getSuspendedUntil().isAfter(now));
    }

    private void notifyMatches(Deal deal, List<MatchingDealRecipient> candidates, Instant now) {
        UUID storeId = deal.getMerchant().getUser().getPublicId();
        List<Pending> pending = new ArrayList<>();
        for (MatchingDealRecipient candidate : candidates) {
            scorer.score(candidate).ifPresent(match -> pending.add(new Pending(
                    UUID.randomUUID(), candidate, match.reason(), message(match.reason(), deal)
            )));
        }
        if (pending.isEmpty()) {
            return;
        }

        int[] inserted = jdbc.batchUpdate("""
                insert into notifications(public_id, recipient_user_id, type, match_reason,
                    deal_public_id, store_public_id, title, message, image_url, created_at)
                values (?, ?, 'MATCHING_DEAL', ?, ?, ?, ?, ?, ?, ?)
                on conflict (recipient_user_id, type, deal_public_id) where type = 'MATCHING_DEAL'
                do nothing
                """, pending.stream().map(p -> new Object[]{
                p.notificationId(),
                p.candidate().getUserId(),
                p.reason().name(),
                deal.getPublicId(),
                storeId,
                TITLE,
                p.message(),
                deal.getPrimaryImageUrl(),
                Timestamp.from(now)
        }).toList());

        for (int i = 0; i < inserted.length; i++) {
            if (inserted[i] == 0) {
                continue; // already notified for this deal (e.g. by the premium saved-filter job)
            }
            if (inserted[i] < 0 && inserted[i] != Statement.SUCCESS_NO_INFO) {
                continue;
            }
            Pending p = pending.get(i);
            eventPublisher.publishEvent(new NotificationCreatedEvent(
                    p.candidate().getUserId(),
                    new NotificationResponse(
                            p.notificationId(),
                            NotificationType.MATCHING_DEAL,
                            p.reason(),
                            deal.getPublicId(),
                            null,
                            storeId,
                            TITLE,
                            p.message(),
                            deal.getPrimaryImageUrl(),
                            false,
                            now,
                            null
                    )
            ));
            if (p.candidate().getEmailEnabled()) {
                eventPublisher.publishEvent(new MatchingDealEmailEvent(
                        p.candidate().getEmail(),
                        p.notificationId(),
                        deal.getName(),
                        deal.getMerchant().getStoreName(),
                        deal.getOriginalPrice(),
                        deal.getDiscountedPrice()
                ));
            }
        }
    }

    static String message(DealMatchReason reason, Deal deal) {
        String storeName = deal.getMerchant().getStoreName();
        return switch (reason) {
            case FAVORITE_STORE -> "%s vừa đăng deal %s.".formatted(storeName, deal.getName());
            case FAVORITE_ITEM -> "%s bạn quan tâm vừa có deal mới tại %s."
                    .formatted(deal.getName(), storeName);
            case FAVORITE_CATEGORY -> "Món bạn yêu thích đang giảm giá: %s tại %s."
                    .formatted(deal.getName(), storeName);
            case ORDER_HISTORY -> "Món giống món bạn hay đặt: %s đang có deal tại %s."
                    .formatted(deal.getName(), storeName);
            case NEARBY_AREA -> "Có deal %s gần khu vực bạn theo dõi tại %s."
                    .formatted(deal.getName(), storeName);
            case SAVED_FILTER -> "Deal %s phù hợp bộ lọc đã lưu của bạn tại %s."
                    .formatted(deal.getName(), storeName);
            case BIG_DISCOUNT -> "%s đang giảm hơn 50%% tại %s."
                    .formatted(deal.getName(), storeName);
        };
    }

    private record Pending(UUID notificationId, MatchingDealRecipient candidate, DealMatchReason reason,
                           String message) {
    }

    public static final class BatchFailure extends RuntimeException {
        private final Long dealId;
        private final long lastUserId;

        BatchFailure(Long dealId, long lastUserId, RuntimeException cause) {
            super("Deal match batch failed", cause);
            this.dealId = dealId;
            this.lastUserId = lastUserId;
        }

        public Long dealId() {
            return dealId;
        }

        public long lastUserId() {
            return lastUserId;
        }
    }
}
