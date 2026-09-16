package com.lootfast.backend.favorite.repository;

import com.lootfast.backend.favorite.entity.UserFavorite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;
import java.util.Optional;

public interface UserFavoriteRepository extends JpaRepository<UserFavorite, Long> {

    @Query("""
            select favorite
            from UserFavorite favorite
            join fetch favorite.deal deal
            join fetch deal.merchant merchant
            join fetch merchant.user merchantUser
            where favorite.user.id = :userId
              and deal.publicId = :dealId
            """)
    Optional<UserFavorite> findDealFavorite(
            @Param("userId") Long userId,
            @Param("dealId") UUID dealId
    );

    @Query("""
            select favorite
            from UserFavorite favorite
            join fetch favorite.merchantProfile store
            join fetch store.user storeUser
            where favorite.user.id = :userId
              and storeUser.publicId = :merchantId
            """)
    Optional<UserFavorite> findStoreFavorite(
            @Param("userId") Long userId,
            @Param("merchantId") UUID merchantId
    );

    long countByUserId(Long userId);

    @Modifying(flushAutomatically = true)
    long deleteByUserIdAndDealPublicId(Long userId, UUID dealId);

    @Modifying(flushAutomatically = true)
    long deleteByUserIdAndMerchantProfileUserPublicId(Long userId, UUID merchantId);

    @Query(
            value = """
                    select favorite
                    from UserFavorite favorite
                    left join fetch favorite.deal deal
                    left join fetch deal.merchant dealMerchant
                    left join fetch dealMerchant.user dealMerchantUser
                    left join fetch favorite.merchantProfile store
                    left join fetch store.user storeUser
                    where favorite.user.id = :userId
                    """,
            countQuery = """
                    select count(favorite)
                    from UserFavorite favorite
                    where favorite.user.id = :userId
                    """
    )
    Page<UserFavorite> findPageByUserId(
            @Param("userId") Long userId,
            Pageable pageable
    );
}
