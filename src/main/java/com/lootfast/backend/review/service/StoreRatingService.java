package com.lootfast.backend.review.service;

import com.lootfast.backend.review.dto.ReviewResponses.Summary;
import com.lootfast.backend.review.repository.StoreReviewRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreRatingService {
  private final StoreReviewRepository reviews;
  public StoreRatingService(StoreReviewRepository reviews) { this.reviews = reviews; }

  @Transactional(readOnly = true)
  public Summary get(Long merchantId) {
    var totals = reviews.summarize(merchantId);
    return new Summary(totals.getAverage() == null ? null
        : BigDecimal.valueOf(totals.getAverage()).setScale(2, RoundingMode.HALF_UP), totals.getCount());
  }
}
