package com.lootfast.backend.review.entity;

import com.lootfast.backend.user.entity.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "review_moderation_events")
public class ReviewModerationEvent {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "review_id", updatable = false) private StoreReview review;
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "admin_id", updatable = false) private User admin;
  @Column(nullable = false, updatable = false) private boolean hidden;
  @Column(nullable = false, length = 1000, updatable = false) private String reason;
  @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
  protected ReviewModerationEvent() {}
  public ReviewModerationEvent(StoreReview review, User admin, boolean hidden, String reason, Instant now) {
    this.review = review; this.admin = admin; this.hidden = hidden; this.reason = reason; this.createdAt = now;
  }
  public User getAdmin() { return admin; }
  public boolean isHidden() { return hidden; }
  public String getReason() { return reason; }
  public Instant getCreatedAt() { return createdAt; }
}
