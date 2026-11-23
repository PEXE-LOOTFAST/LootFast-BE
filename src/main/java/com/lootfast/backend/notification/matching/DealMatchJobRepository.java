package com.lootfast.backend.notification.matching;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface DealMatchJobRepository extends JpaRepository<DealMatchJob, Long> {

    /**
     * Claims the oldest due job. {@code SKIP LOCKED} lets several instances run the worker at
     * once without blocking on, or double-processing, the same job.
     */
    @Query(value = """
            select *
            from deal_match_jobs
            where completed_at is null
              and failed_at is null
              and next_attempt_at <= :now
            order by next_attempt_at, deal_id
            limit 1
            for update skip locked
            """, nativeQuery = true)
    Optional<DealMatchJob> lockNextDue(@Param("now") Instant now);

    /**
     * Records a failed batch with exponential backoff (30s, 2m, 10m, 30m) and gives up after
     * {@link DealMatchJob#MAX_FAILURES} attempts. The cursor check ignores a stale failure report
     * if another worker already made progress.
     */
    @Modifying
    @Query(value = """
            update deal_match_jobs set
                next_attempt_at = :retryAt,
                failure_count = failure_count + 1,
                failed_at = case when failure_count + 1 >= :maxFailures then :now else null end,
                last_error_code = :errorCode
            where deal_id = :dealId
              and last_user_id = :lastUserId
              and completed_at is null
              and failed_at is null
            """, nativeQuery = true)
    int recordFailure(
            @Param("dealId") Long dealId,
            @Param("lastUserId") long lastUserId,
            @Param("now") Instant now,
            @Param("retryAt") Instant retryAt,
            @Param("maxFailures") int maxFailures,
            @Param("errorCode") String errorCode
    );
}
