package com.lootfast.backend.notification.repository;

import com.lootfast.backend.notification.entity.NotificationPreference;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface NotificationPreferenceRepository
        extends JpaRepository<NotificationPreference, Long> {

    Optional<NotificationPreference> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select preference from NotificationPreference preference "
            + "where preference.user.id = :userId")
    Optional<NotificationPreference> findByUserIdForUpdate(@Param("userId") Long userId);
}
