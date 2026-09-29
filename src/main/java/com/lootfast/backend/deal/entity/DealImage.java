package com.lootfast.backend.deal.entity;

import com.lootfast.backend.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "deal_images")
public class DealImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deal_id", nullable = false, updatable = false)
    private Deal deal;

    @Column(name = "storage_key", length = 255, updatable = false)
    private String storageKey;

    @Column(name = "image_url", nullable = false, length = 2048, updatable = false)
    private String imageUrl;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private DealImageStatus status;

    @Column(name = "removed_at")
    private Instant removedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "removed_by_user_id")
    private User removedBy;

    @Column(name = "removal_reason", length = 500)
    private String removalReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DealImage() {
    }

    public DealImage(Deal deal, String storageKey, String imageUrl, int displayOrder) {
        this.deal = deal;
        this.storageKey = storageKey;
        this.imageUrl = imageUrl;
        this.displayOrder = displayOrder;
        this.status = DealImageStatus.ACTIVE;
    }

    @PrePersist
    private void beforeInsert() {
        publicId = UUID.randomUUID();
        createdAt = Instant.now();
    }

    public void reorder(int displayOrder) {
        if (status != DealImageStatus.ACTIVE) {
            throw new IllegalStateException("Removed images cannot be reordered");
        }
        this.displayOrder = displayOrder;
    }

    public void remove(User actor, String reason, Instant now) {
        if (status == DealImageStatus.REMOVED) {
            return;
        }
        status = DealImageStatus.REMOVED;
        removedBy = actor;
        removalReason = reason;
        removedAt = now;
    }

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public Deal getDeal() {
        return deal;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public DealImageStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
