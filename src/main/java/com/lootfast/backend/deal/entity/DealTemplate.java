package com.lootfast.backend.deal.entity;

import com.lootfast.backend.merchant.entity.MerchantProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "deal_templates",
    indexes = {
      @Index(
          name = "idx_deal_templates_merchant_created",
          columnList = "merchant_profile_id, created_at DESC")
    })
public class DealTemplate {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "public_id", nullable = false, unique = true, updatable = false)
  private UUID publicId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "merchant_profile_id", nullable = false, updatable = false)
  private MerchantProfile merchant;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "source_deal_id", updatable = false)
  private Deal sourceDeal;

  @Column(nullable = false, length = 160)
  private String name;

  @Column(name = "category_code", nullable = false, length = 100)
  private String categoryCode;

  @Column(name = "original_price", nullable = false, precision = 12, scale = 2)
  private BigDecimal originalPrice;

  @Column(name = "discounted_price", nullable = false, precision = 12, scale = 2)
  private BigDecimal discountedPrice;

  @Column(name = "default_quantity", nullable = false)
  private int defaultQuantity;

  @Column(name = "preparation_minutes", nullable = false)
  private int preparationMinutes;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Version
  @Column(nullable = false)
  private long version;

  protected DealTemplate() {}

  public DealTemplate(Deal source) {
    merchant = source.getMerchant();
    sourceDeal = source;
    name = source.getName();
    categoryCode = source.getCategoryCode();
    originalPrice = source.getOriginalPrice();
    discountedPrice = source.getDiscountedPrice();
    defaultQuantity = source.getTotalQuantity();
    preparationMinutes = source.getPreparationMinutes();
  }

  @PrePersist
  private void beforeInsert() {
    publicId = UUID.randomUUID();
    createdAt = Instant.now();
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

  public Deal getSourceDeal() {
    return sourceDeal;
  }

  public String getName() {
    return name;
  }

  public String getCategoryCode() {
    return categoryCode;
  }

  public BigDecimal getOriginalPrice() {
    return originalPrice;
  }

  public BigDecimal getDiscountedPrice() {
    return discountedPrice;
  }

  public int getDefaultQuantity() {
    return defaultQuantity;
  }

  public int getPreparationMinutes() {
    return preparationMinutes;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public long getVersion() {
    return version;
  }
}
