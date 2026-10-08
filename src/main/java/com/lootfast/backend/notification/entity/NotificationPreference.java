package com.lootfast.backend.notification.entity;

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
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

@Entity
@Table(
        name = "notification_preferences",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_notification_preferences_user",
                columnNames = "user_id"
        )
)
public class NotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Column(name = "matching_deals_enabled", nullable = false)
    private boolean matchingDealsEnabled = true;

    @Column(name = "buyer_order_updates_enabled", nullable = false)
    private boolean buyerOrderUpdatesEnabled = true;

    @Column(name = "merchant_order_updates_enabled", nullable = false)
    private boolean merchantOrderUpdatesEnabled = true;

    @Column(name = "pickup_reminders_enabled", nullable = false)
    private boolean pickupRemindersEnabled = true;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NotificationPreference() {
    }

    public NotificationPreference(User user) {
        this.user = user;
    }

    public void update(
            boolean matchingDealsEnabled,
            boolean buyerOrderUpdatesEnabled,
            boolean merchantOrderUpdatesEnabled,
            boolean pickupRemindersEnabled,
            boolean emailEnabled
    ) {
        this.matchingDealsEnabled = matchingDealsEnabled;
        this.buyerOrderUpdatesEnabled = buyerOrderUpdatesEnabled;
        this.merchantOrderUpdatesEnabled = merchantOrderUpdatesEnabled;
        this.pickupRemindersEnabled = pickupRemindersEnabled;
        this.emailEnabled = emailEnabled;
    }

    @PrePersist
    private void beforeInsert() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    private void beforeUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isMatchingDealsEnabled() {
        return matchingDealsEnabled;
    }

    public boolean isBuyerOrderUpdatesEnabled() {
        return buyerOrderUpdatesEnabled;
    }

    public boolean isMerchantOrderUpdatesEnabled() {
        return merchantOrderUpdatesEnabled;
    }

    public boolean isPickupRemindersEnabled() {
        return pickupRemindersEnabled;
    }

    public boolean isEmailEnabled() {
        return emailEnabled;
    }
}
