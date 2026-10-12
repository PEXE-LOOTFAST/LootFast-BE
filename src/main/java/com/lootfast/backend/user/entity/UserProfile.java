package com.lootfast.backend.user.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Entity
@Table(
        name = "user_profiles",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_profiles_user_id",
                columnNames = "user_id"
        )
)
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "avatar_url", length = 2048)
    private String avatarUrl;

    @Column(name = "avatar_storage_key", length = 255)
    private String avatarStorageKey;

    @Column(name = "pickup_area", length = 255)
    private String pickupArea;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "user_profile_favorite_categories",
            joinColumns = @JoinColumn(name = "user_profile_id")
    )
    @Column(name = "category_code", nullable = false, length = 100)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Set<String> favoriteCategories = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserProfile() {
    }

    public UserProfile(User user, String displayName) {
        this.user = user;
        this.displayName = displayName;
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

    public String getDisplayName() {
        return displayName;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public String getPickupArea() {
        return pickupArea;
    }

    public void replaceAvatar(String storageKey, String url) {
        this.avatarStorageKey = storageKey;
        this.avatarUrl = url;
    }

    public void rename(String displayName) {
        this.displayName = displayName;
    }

    public void updateDiscoveryPreferences(
            String pickupArea,
            Iterable<String> categoryCodes
    ) {
        this.pickupArea = pickupArea == null || pickupArea.isBlank()
                ? null
                : pickupArea.trim();
        favoriteCategories.clear();
        if (categoryCodes != null) {
            categoryCodes.forEach(categoryCode -> {
                if (categoryCode != null && !categoryCode.isBlank()) {
                    favoriteCategories.add(categoryCode.trim().toUpperCase(Locale.ROOT));
                }
            });
        }
    }

    public List<String> getFavoriteCategories() {
        return List.copyOf(favoriteCategories);
    }
}
