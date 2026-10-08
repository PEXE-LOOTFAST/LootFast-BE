package com.lootfast.backend.review.repository;

import com.lootfast.backend.review.entity.StoreReview;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface StoreReviewRepository extends JpaRepository<StoreReview, Long> {
  boolean existsByOrderId(Long orderId);
  @EntityGraph(attributePaths = {"buyer", "merchant.user", "order"})
  Optional<StoreReview> findByOrderPublicId(UUID orderId);
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from StoreReview r where r.publicId = :id")
  Optional<StoreReview> lock(UUID id);
  @EntityGraph(attributePaths = {"buyer", "merchant.user", "order"})
  Page<StoreReview> findByMerchantIdAndHiddenFalse(Long merchantId, Pageable page);
  @EntityGraph(attributePaths = {"buyer", "merchant.user", "order"})
  Page<StoreReview> findByMerchantUserId(Long userId, Pageable page);
  @Override
  @EntityGraph(attributePaths = {"buyer", "merchant.user", "order"})
  Page<StoreReview> findAll(Pageable page);
  interface Totals { Double getAverage(); long getCount(); }
  @Query("select avg(r.rating) as average, count(r) as count from StoreReview r where r.merchant.id=:merchantId and r.hidden=false")
  Totals summarize(Long merchantId);
}
