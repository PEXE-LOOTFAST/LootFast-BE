package com.lootfast.backend.favorite.entity;

import com.lootfast.backend.deal.entity.Deal;
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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

@Entity
@Table(
        name = "user_favorites",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_user_favorites_deal",
                        columnNames = {"user_id", "deal_id"}
                ),
                @UniqueConstraint(
                        name = "uk_user_favorites_store",
                        columnNames = {"user_id", "merchant_profile_id"}
                )
        }
)
@Check(constraints = "(favorite_type = 'DEAL' and deal_id is not null and merchant_profile_id is null) "
        + "or (favorite_type = 'STORE' and deal_id is null and merchant_profile_id is not null)")
public class UserFavorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "favorite_type", nullable = false, length = 16, updatable = false)
    private FavoriteType favoriteType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deal_id", updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Deal deal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_profile_id", updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private MerchantProfile merchantProfile;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UserFavorite() {
    }

    private UserFavorite(
            User user,
            FavoriteType favoriteType,
            Deal deal,
            MerchantProfile merchantProfile
    ) {
        this.user = user;
        this.favoriteType = favoriteType;
        this.deal = deal;
        this.merchantProfile = merchantProfile;
    }

    public static UserFavorite deal(User user, Deal deal) {
        return new UserFavorite(user, FavoriteType.DEAL, deal, null);
    }

    public static UserFavorite store(User user, MerchantProfile merchantProfile) {
        return new UserFavorite(user, FavoriteType.STORE, null, merchantProfile);
    }

    @PrePersist
    private void beforeInsert() {
        createdAt = Instant.now();
    }

    public FavoriteType getFavoriteType() {
        return favoriteType;
    }

    public Deal getDeal() {
        return deal;
    }

    public MerchantProfile getMerchantProfile() {
        return merchantProfile;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
