package com.lootfast.backend.user.repository;

import com.lootfast.backend.user.entity.UserProfile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    Optional<UserProfile> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update UserProfile profile
            set profile.avatarStorageKey = :storageKey,
                profile.avatarUrl = :url,
                profile.updatedAt = :updatedAt
            where profile.user.id = :userId
            """)
    int updateAvatarByUserId(
            @Param("userId") Long userId,
            @Param("storageKey") String storageKey,
            @Param("url") String url,
            @Param("updatedAt") Instant updatedAt
    );
}
