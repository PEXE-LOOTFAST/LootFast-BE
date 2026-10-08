package com.lootfast.backend.review.entity;

import com.lootfast.backend.order.entity.CustomerOrder;
import com.lootfast.backend.merchant.entity.MerchantProfile;
import com.lootfast.backend.user.entity.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "store_reviews")
public class StoreReview {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @Column(name = "public_id", nullable = false, unique = true, updatable = false) private UUID publicId;
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_id", unique = true, updatable = false) private CustomerOrder order;
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "buyer_id", updatable = false) private User buyer;
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "merchant_id", updatable = false) private MerchantProfile merchant;
  @Column(nullable = false, updatable = false) private int rating;
  @Column(length = 1000, updatable = false) private String comment;
  @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
  @Column(length = 1000) private String reply;
  @Column(name = "replied_at") private Instant repliedAt;
  @Column(nullable = false) private boolean hidden;

  protected StoreReview() {}
  public StoreReview(CustomerOrder order, int rating, String comment, Instant now) {
    this.publicId = UUID.randomUUID();
    this.order = order; this.buyer = order.getBuyer(); this.merchant = order.getMerchant();
    this.rating = rating; this.comment = comment; this.createdAt = now;
  }
  public void reply(String text, Instant now) { this.reply = text; this.repliedAt = now; }
  public void setHidden(boolean hidden) { this.hidden = hidden; }
  public Long getId() { return id; }
  public UUID getPublicId() { return publicId; }
  public CustomerOrder getOrder() { return order; }
  public User getBuyer() { return buyer; }
  public MerchantProfile getMerchant() { return merchant; }
  public int getRating() { return rating; }
  public String getComment() { return comment; }
  public Instant getCreatedAt() { return createdAt; }
  public String getReply() { return reply; }
  public Instant getRepliedAt() { return repliedAt; }
  public boolean isHidden() { return hidden; }
}
