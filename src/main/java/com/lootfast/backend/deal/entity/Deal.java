package com.lootfast.backend.deal.entity;

import com.lootfast.backend.common.text.SearchNormalizer;
import com.lootfast.backend.merchant.entity.MerchantProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "deals",
        indexes = {
                @Index(
                        name = "idx_deals_feed",
                        columnList = "status, available_from, available_until"
                ),
                @Index(name = "idx_deals_merchant_id", columnList = "merchant_profile_id")
        }
)
public class Deal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_profile_id", nullable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private MerchantProfile merchant;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "search_name", nullable = false, length = 160)
    private String searchName;

    @Column(name = "category_code", nullable = false, length = 100)
    private String categoryCode;

    @Column(name = "original_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal originalPrice;

    @Column(name = "discounted_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountedPrice;

    @Column(name = "remaining_quantity", nullable = false)
    private int remainingQuantity;

    @Column(name = "primary_image_url", nullable = false, length = 2048)
    private String primaryImageUrl;

    @Column(name = "preparation_minutes", nullable = false)
    private int preparationMinutes;

    @Column(name = "pickup_start", nullable = false)
    private Instant pickupStart;

    @Column(name = "pickup_end", nullable = false)
    private Instant pickupEnd;

    @Column(name = "available_from", nullable = false)
    private Instant availableFrom;

    @Column(name = "available_until", nullable = false)
    private Instant availableUntil;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private DealStatus status;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Deal() {
    }

    public Deal(
            MerchantProfile merchant,
            String name,
            String categoryCode,
            BigDecimal originalPrice,
            BigDecimal discountedPrice,
            int remainingQuantity,
            String primaryImageUrl,
            int preparationMinutes,
            Instant pickupStart,
            Instant pickupEnd,
            Instant availableFrom,
            Instant availableUntil,
            DealStatus status
    ) {
        this.merchant = merchant;
        this.name = name;
        this.categoryCode = categoryCode;
        this.originalPrice = originalPrice;
        this.discountedPrice = discountedPrice;
        this.remainingQuantity = remainingQuantity;
        this.primaryImageUrl = primaryImageUrl;
        this.preparationMinutes = preparationMinutes;
        this.pickupStart = pickupStart;
        this.pickupEnd = pickupEnd;
        this.availableFrom = availableFrom;
        this.availableUntil = availableUntil;
        this.status = status;
    }

    @PrePersist
    private void beforeInsert() {
        Instant now = Instant.now();
        publicId = UUID.randomUUID();
        searchName = SearchNormalizer.normalize(name);
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    private void beforeUpdate() {
        searchName = SearchNormalizer.normalize(name);
        updatedAt = Instant.now();
    }

    public UUID getPublicId() {
        return publicId;
    }

    public MerchantProfile getMerchant() {
        return merchant;
    }

    public String getName() {
        return name;
    }

    public String getCategoryCode() {
        return categoryCode;
    }

    public String getSearchName() {
        return searchName;
    }

    public BigDecimal getOriginalPrice() {
        return originalPrice;
    }

    public BigDecimal getDiscountedPrice() {
        return discountedPrice;
    }

    public int getRemainingQuantity() {
        return remainingQuantity;
    }

    public String getPrimaryImageUrl() {
        return primaryImageUrl;
    }

    public int getPreparationMinutes() {
        return preparationMinutes;
    }

    public Instant getPickupStart() {
        return pickupStart;
    }

    public Instant getPickupEnd() {
        return pickupEnd;
    }

    public Instant getAvailableFrom() {
        return availableFrom;
    }

    public Instant getAvailableUntil() {
        return availableUntil;
    }

    public DealStatus getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
