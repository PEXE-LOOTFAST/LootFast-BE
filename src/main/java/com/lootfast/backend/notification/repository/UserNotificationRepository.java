package com.lootfast.backend.notification.repository;

import com.lootfast.backend.notification.entity.NotificationType;
import com.lootfast.backend.notification.entity.UserNotification;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserNotificationRepository extends JpaRepository<UserNotification, Long> {

    boolean existsByRecipientIdAndTypeAndOrderPublicIdAndOccurrenceKey(Long recipientId, NotificationType type, UUID orderId, UUID occurrenceKey);

    java.util.Optional<UserNotification> findByPublicId(UUID publicId);

    Page<UserNotification> findByRecipientId(Long recipientId, Pageable pageable);

    long countByRecipientIdAndReadAtIsNull(Long recipientId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select notification
            from UserNotification notification
            where notification.publicId = :notificationId
              and notification.recipient.id = :recipientId
            """)
    Optional<UserNotification> findOwnedForUpdate(
            @Param("notificationId") UUID notificationId,
            @Param("recipientId") Long recipientId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update UserNotification notification
            set notification.readAt = :readAt
            where notification.recipient.id = :recipientId
              and notification.readAt is null
            """)
    int markAllRead(
            @Param("recipientId") Long recipientId,
            @Param("readAt") Instant readAt
    );

    @Query("""
            select notification.recipient.id
            from UserNotification notification
            where notification.type = :type
              and notification.dealPublicId = :dealId
              and notification.recipient.id in :recipientIds
            """)
    List<Long> findExistingRecipientIds(
            @Param("type") NotificationType type,
            @Param("dealId") UUID dealId,
            @Param("recipientIds") Collection<Long> recipientIds
    );
}
