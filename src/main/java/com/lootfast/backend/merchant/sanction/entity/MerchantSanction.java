package com.lootfast.backend.merchant.sanction.entity;

import com.lootfast.backend.merchant.entity.MerchantProfile;
import com.lootfast.backend.user.entity.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "merchant_sanctions",
    uniqueConstraints =
        @UniqueConstraint(name = "uk_merchant_sanctions_public", columnNames = "public_id"))
public class MerchantSanction {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "public_id", nullable = false, updatable = false)
  private UUID publicId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "merchant_profile_id", updatable = false)
  private MerchantProfile merchant;

  @Enumerated(EnumType.STRING)
  @Column(name = "sanction_type", nullable = false, length = 16, updatable = false)
  private SanctionType sanctionType;

  @Column(nullable = false, length = 1000, updatable = false)
  private String reason;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "created_by_user_id", updatable = false)
  private User createdBy;

  @Column(name = "starts_at", nullable = false, updatable = false)
  private Instant startsAt;

  @Column(name = "ends_at")
  private Instant endsAt;

  @Column(name = "lifted_at")
  private Instant liftedAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "lifted_by_user_id")
  private User liftedBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected MerchantSanction() {}

  private MerchantSanction(
      MerchantProfile merchant,
      SanctionType type,
      String reason,
      User createdBy,
      Instant startsAt,
      Instant endsAt) {
    this.publicId = UUID.randomUUID();
    this.merchant = merchant;
    this.sanctionType = type;
    this.reason = reason;
    this.createdBy = createdBy;
    this.startsAt = startsAt;
    this.endsAt = endsAt;
    this.createdAt = startsAt;
  }

  public static MerchantSanction warning(
      MerchantProfile merchant, User admin, String reason, Instant now) {
    return new MerchantSanction(merchant, SanctionType.WARNING, reason, admin, now, null);
  }

  public static MerchantSanction suspension(
      MerchantProfile merchant, User admin, String reason, Instant now, Instant until) {
    return new MerchantSanction(merchant, SanctionType.SUSPENSION, reason, admin, now, until);
  }

  public boolean isActive(Instant now) {
    return liftedAt == null && (endsAt == null || endsAt.isAfter(now));
  }

  public void lift(User admin, Instant now) {
    this.liftedAt = now;
    this.liftedBy = admin;
  }

  public Long getId() {
    return id;
  }

  public UUID getPublicId() {
    return publicId;
  }

  public MerchantProfile getMerchant() {
    return merchant;
  }

  public SanctionType getSanctionType() {
    return sanctionType;
  }

  public String getReason() {
    return reason;
  }

  public Instant getStartsAt() {
    return startsAt;
  }

  public Instant getEndsAt() {
    return endsAt;
  }

  public Instant getLiftedAt() {
    return liftedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
