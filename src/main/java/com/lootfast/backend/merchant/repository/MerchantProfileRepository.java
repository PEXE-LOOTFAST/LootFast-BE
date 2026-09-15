package com.lootfast.backend.merchant.repository;

import com.lootfast.backend.merchant.entity.MerchantProfile;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface MerchantProfileRepository extends JpaRepository<MerchantProfile, Long> {

    Optional<MerchantProfile> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select profile from MerchantProfile profile where profile.user.id = :userId")
    Optional<MerchantProfile> findByUserIdForUpdate(@Param("userId") Long userId);

    boolean existsByUserId(Long userId);

    @Query("""
            select profile
            from MerchantProfile profile
            join fetch profile.user merchantUser
            where merchantUser.publicId = :publicId
              and profile.storeName is not null
              and profile.latitude is not null
              and profile.longitude is not null
            """)
    Optional<MerchantProfile> findPublicByUserPublicId(@Param("publicId") UUID publicId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update MerchantProfile profile
            set profile.logoStorageKey = :storageKey,
                profile.logoUrl = :url,
                profile.updatedAt = :updatedAt
            where profile.user.id = :userId
            """)
    int updateLogoByUserId(
            @Param("userId") Long userId,
            @Param("storageKey") String storageKey,
            @Param("url") String url,
            @Param("updatedAt") Instant updatedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update MerchantProfile profile
            set profile.coverStorageKey = :storageKey,
                profile.coverUrl = :url,
                profile.updatedAt = :updatedAt
            where profile.user.id = :userId
            """)
    int updateCoverByUserId(
            @Param("userId") Long userId,
            @Param("storageKey") String storageKey,
            @Param("url") String url,
            @Param("updatedAt") Instant updatedAt
    );
}
