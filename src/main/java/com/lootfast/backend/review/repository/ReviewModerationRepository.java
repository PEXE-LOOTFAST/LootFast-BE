package com.lootfast.backend.review.repository;

import com.lootfast.backend.review.entity.ReviewModerationEvent;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import java.util.UUID;

public interface ReviewModerationRepository extends JpaRepository<ReviewModerationEvent, Long> {
  @EntityGraph(attributePaths = "admin")
  Page<ReviewModerationEvent> findByReviewPublicId(UUID reviewId, Pageable page);
}
