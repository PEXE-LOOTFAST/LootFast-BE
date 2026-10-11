package com.lootfast.backend.review.dto;
import com.lootfast.backend.review.entity.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
public final class ReviewResponses {
  private ReviewResponses() {}
  public record PublicReview(UUID reviewId, UUID merchantId, String authorName, int rating,
      String comment, Instant createdAt, String merchantReply, Instant repliedAt) {
    public static PublicReview from(StoreReview r) {
      return new PublicReview(r.getPublicId(), r.getMerchant().getUser().getPublicId(),
          r.getBuyer().getFirstName(), r.getRating(), r.getComment(), r.getCreatedAt(), r.getReply(), r.getRepliedAt());
    }
  }
  public record ManagedReview(PublicReview review, UUID orderId, boolean hidden) {
    public static ManagedReview from(StoreReview r) {
      return new ManagedReview(PublicReview.from(r), r.getOrder().getPublicId(), r.isHidden());
    }
  }
  public record Summary(BigDecimal averageRating, long reviewCount) {}
  public record Moderation(UUID adminId, boolean hidden, String reason, Instant createdAt) {
    public static Moderation from(ReviewModerationEvent e) {
      return new Moderation(e.getAdmin().getPublicId(), e.isHidden(), e.getReason(), e.getCreatedAt());
    }
  }
}
