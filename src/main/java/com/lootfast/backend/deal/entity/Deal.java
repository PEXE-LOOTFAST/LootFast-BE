package com.lootfast.backend.deal.entity;

import com.lootfast.backend.common.text.SearchNormalizer;
import com.lootfast.backend.merchant.entity.MerchantProfile;
import com.lootfast.backend.user.entity.User;
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

    @Column(name = "total_quantity", nullable = false)
    private int totalQuantity;

    @Column(name = "primary_image_url", length = 2048)
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

    @Column(name = "hidden_at")
    private Instant hiddenAt;

    @Column(name = "removed_at")
    private Instant removedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "removed_by_user_id")
    private User removedBy;

    @Column(name = "removal_reason", length = 500)
    private String removalReason;

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
        this.totalQuantity = remainingQuantity;
        this.primaryImageUrl = primaryImageUrl;
        this.preparationMinutes = preparationMinutes;
        // PostgreSQL stores timestamptz at microsecond precision. Normalize at the boundary so
        // the one-window-per-day invariant compares consistently on H2 and PostgreSQL.
        this.pickupStart = pickupStart.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        this.pickupEnd = pickupEnd.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        this.availableFrom = availableFrom.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        this.availableUntil = availableUntil.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
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

    public void reserve(int quantity) {
        if (quantity <= 0 || quantity > remainingQuantity) throw new IllegalArgumentException("Invalid reservation quantity");
        remainingQuantity -= quantity;
        if (remainingQuantity == 0) status = DealStatus.SOLD_OUT;
    }

    public void restore(int quantity, Instant now) {
        if (quantity <= 0) throw new IllegalArgumentException("Invalid restore quantity");
        remainingQuantity += quantity;
        if (status == DealStatus.SOLD_OUT
                && !now.isBefore(availableFrom)
                && now.isBefore(availableUntil)) {
            status = DealStatus.ACTIVE;
        }
    }

    public void updateDraft(
            String name,
            String categoryCode,
            BigDecimal originalPrice,
            BigDecimal discountedPrice,
            int totalQuantity,
            int preparationMinutes,
            Instant pickupStart,
            Instant pickupEnd,
            Instant availableFrom,
            Instant availableUntil
    ) {
        if (status != DealStatus.DRAFT) {
            throw new IllegalStateException("Only draft deals can be edited");
        }
        this.name = name;
        this.categoryCode = categoryCode;
        this.originalPrice = originalPrice;
        this.discountedPrice = discountedPrice;
        this.totalQuantity = totalQuantity;
        this.remainingQuantity = totalQuantity;
        this.preparationMinutes = preparationMinutes;
        this.pickupStart = microsecond(pickupStart);
        this.pickupEnd = microsecond(pickupEnd);
        this.availableFrom = microsecond(availableFrom);
        this.availableUntil = microsecond(availableUntil);
    }

    public void publish(String primaryImageUrl) {
        if (status != DealStatus.DRAFT) {
            throw new IllegalStateException("Only draft deals can be published");
        }
        this.primaryImageUrl = primaryImageUrl;
        this.status = remainingQuantity == 0 ? DealStatus.SOLD_OUT : DealStatus.ACTIVE;
    }

    public void updatePrimaryImage(String primaryImageUrl) {
        this.primaryImageUrl = primaryImageUrl;
    }

    public void hide(Instant now) {
        if (status == DealStatus.HIDDEN) {
            return;
        }
        if (status != DealStatus.ACTIVE && status != DealStatus.SOLD_OUT) {
            throw new IllegalStateException("Only visible deals can be hidden");
        }
        status = DealStatus.HIDDEN;
        hiddenAt = now;
    }

    public void restoreVisibility() {
        if (status != DealStatus.HIDDEN) {
            throw new IllegalStateException("Only hidden deals can be restored");
        }
        status = remainingQuantity == 0 ? DealStatus.SOLD_OUT : DealStatus.ACTIVE;
        hiddenAt = null;
    }

    public void remove(User actor, String reason, Instant now) {
        if (status == DealStatus.REMOVED) {
            return;
        }
        status = DealStatus.REMOVED;
        hiddenAt = null;
        removedAt = now;
        removedBy = actor;
        removalReason = reason;
    }

    public void changeTotalQuantity(int newTotalQuantity, Instant now) {
        int committedQuantity = totalQuantity - remainingQuantity;
        if (newTotalQuantity < committedQuantity) {
            throw new IllegalArgumentException("Total quantity is below committed quantity");
        }
        totalQuantity = newTotalQuantity;
        remainingQuantity = newTotalQuantity - committedQuantity;
        if (remainingQuantity == 0 && status == DealStatus.ACTIVE) {
            status = DealStatus.SOLD_OUT;
        } else if (remainingQuantity > 0
                && status == DealStatus.SOLD_OUT
                && !now.isBefore(availableFrom)
                && now.isBefore(availableUntil)) {
            status = DealStatus.ACTIVE;
        }
    }

    private static Instant microsecond(Instant value) {
        return value.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
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

    public int getTotalQuantity() {
        return totalQuantity;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getHiddenAt() {
        return hiddenAt;
    }

    public Instant getRemovedAt() {
        return removedAt;
    }

    public User getRemovedBy() {
        return removedBy;
    }

    public String getRemovalReason() {
        return removalReason;
    }
}
