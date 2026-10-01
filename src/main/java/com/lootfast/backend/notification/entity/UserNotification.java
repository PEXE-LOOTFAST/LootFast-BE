package com.lootfast.backend.notification.entity;

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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "notifications",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_notifications_recipient_type_deal",
                columnNames = {"recipient_user_id", "type", "deal_public_id"}
        ),
        indexes = @Index(
                name = "idx_notifications_recipient_created",
                columnList = "recipient_user_id, created_at"
        )
)
public class UserNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_user_id", nullable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, updatable = false)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_reason", length = 32, updatable = false)
    private DealMatchReason matchReason;

    @Column(name = "deal_public_id", nullable = false, updatable = false)
    private UUID dealPublicId;

    @Column(name = "store_public_id", nullable = false, updatable = false)
    private UUID storePublicId;

    @Column(nullable = false, length = 160, updatable = false)
    private String title;

    @Column(nullable = false, length = 500, updatable = false)
    private String message;

    @Column(name = "image_url", length = 2048, updatable = false)
    private String imageUrl;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UserNotification() {
    }

    public UserNotification(
            User recipient,
            DealMatchReason matchReason,
            UUID dealPublicId,
            UUID storePublicId,
            String title,
            String message,
            String imageUrl
    ) {
        this.recipient = recipient;
        this.type = NotificationType.MATCHING_DEAL;
        this.matchReason = matchReason;
        this.dealPublicId = dealPublicId;
        this.storePublicId = storePublicId;
        this.title = title;
        this.message = message;
        this.imageUrl = imageUrl;
    }

    @PrePersist
    private void beforeInsert() {
        publicId = UUID.randomUUID();
        createdAt = Instant.now();
    }

    public void markRead(Instant now) {
        if (readAt == null) {
            readAt = now;
        }
    }

    public UUID getPublicId() {
        return publicId;
    }

    public User getRecipient() {
        return recipient;
    }

    public NotificationType getType() {
        return type;
    }

    public DealMatchReason getMatchReason() {
        return matchReason;
    }

    public UUID getDealPublicId() {
        return dealPublicId;
    }

    public UUID getStorePublicId() {
        return storePublicId;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
