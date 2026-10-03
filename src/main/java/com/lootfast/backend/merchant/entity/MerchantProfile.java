package com.lootfast.backend.merchant.entity;

import com.lootfast.backend.common.text.SearchNormalizer;
import com.lootfast.backend.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Entity
@DynamicUpdate
@Table(
        name = "merchant_profiles",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_merchant_profiles_user_id",
                columnNames = "user_id"
        )
)
public class MerchantProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Column(name = "store_name", length = 120)
    private String storeName;

    @Column(name = "search_name", length = 120)
    private String searchName;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "business_category", length = 100)
    private String businessCategory;

    @Column(name = "contact_email", length = 254)
    private String contactEmail;

    @Column(name = "contact_phone", length = 16)
    private String contactPhone;

    @Column(name = "address_line", length = 255)
    private String addressLine;

    @Column(name = "ward", length = 100)
    private String ward;

    @Column(name = "district", length = 100)
    private String district;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "logo_url", length = 2048)
    private String logoUrl;

    @Column(name = "logo_storage_key", length = 255)
    private String logoStorageKey;

    @Column(name = "cover_url", length = 2048)
    private String coverUrl;

    @Column(name = "cover_storage_key", length = 255)
    private String coverStorageKey;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "review_flagged_at")
    private Instant reviewFlaggedAt;

    @Column(name = "suspended_until")
    private Instant suspendedUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MerchantProfile() {
    }

    public MerchantProfile(User user) {
        this.user = user;
    }

    public void updateStoreInformation(
            String storeName,
            String description,
            String businessCategory,
            String contactEmail,
            String contactPhone
    ) {
        this.storeName = storeName;
        this.description = description;
        this.businessCategory = businessCategory;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
    }

    public void updateLocation(
            String addressLine,
            String ward,
            String district,
            String city,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
        this.addressLine = addressLine;
        this.ward = ward;
        this.district = district;
        this.city = city;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public void markVerified(Instant approvedAt) {
        Objects.requireNonNull(approvedAt, "approvedAt must not be null");
        if (verifiedAt == null) {
            verifiedAt = approvedAt;
        }
    }

    /** Gắn cờ cho Admin rà soát khi vượt ngưỡng báo cáo. Idempotent: chỉ ghi lần đầu. */
    public void flagForReview(Instant flaggedAt) {
        Objects.requireNonNull(flaggedAt, "flaggedAt must not be null");
        if (reviewFlaggedAt == null) {
            reviewFlaggedAt = flaggedAt;
        }
    }

    @PrePersist
    private void beforeInsert() {
        Instant now = Instant.now();
        searchName = SearchNormalizer.normalize(storeName);
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    private void beforeUpdate() {
        searchName = SearchNormalizer.normalize(storeName);
        updatedAt = Instant.now();
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getStoreName() {
        return storeName;
    }

    public String getSearchName() {
        return searchName;
    }

    public String getDescription() {
        return description;
    }

    public String getBusinessCategory() {
        return businessCategory;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public String getAddressLine() {
        return addressLine;
    }

    public String getWard() {
        return ward;
    }

    public String getDistrict() {
        return district;
    }

    public String getCity() {
        return city;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isVerified() {
        return verifiedAt != null;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public boolean isFlaggedForReview() {
        return reviewFlaggedAt != null;
    }

    public Instant getReviewFlaggedAt() {
        return reviewFlaggedAt;
    }

    public void suspendUntil(Instant until) {
        this.suspendedUntil = until;
    }

    public void liftSuspension() {
        this.suspendedUntil = null;
    }

    public boolean isSuspended(Instant now) {
        return suspendedUntil != null && suspendedUntil.isAfter(now);
    }

    public Instant getSuspendedUntil() {
        return suspendedUntil;
    }
}
