package com.lootfast.backend.notification.matching;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Background job that fans a published deal out to matching buyers, one keyset page at a time. */
@Entity
@Table(name = "deal_match_jobs")
public class DealMatchJob {

    public static final int MAX_FAILURES = 5;

    @Id
    @Column(name = "deal_id", nullable = false, updatable = false)
    private Long dealId;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_user_id", nullable = false)
    private long lastUserId;

    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "last_error_code", length = 64)
    private String lastErrorCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DealMatchJob() {
    }

    public DealMatchJob(Long dealId, Instant now) {
        this.dealId = dealId;
        this.nextAttemptAt = now;
        this.createdAt = now;
    }

    public void advance(long lastUserId) {
        this.lastUserId = lastUserId;
        this.failureCount = 0;
        this.lastErrorCode = null;
    }

    public void complete(Instant now) {
        this.completedAt = now;
        this.failureCount = 0;
        this.lastErrorCode = null;
    }

    public Long getDealId() {
        return dealId;
    }

    public long getLastUserId() {
        return lastUserId;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getFailedAt() {
        return failedAt;
    }
}
