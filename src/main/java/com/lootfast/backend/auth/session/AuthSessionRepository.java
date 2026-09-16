package com.lootfast.backend.auth.session;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AuthSession s join fetch s.user where s.id = :id")
    Optional<AuthSession> findByIdForUpdate(@Param("id") UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AuthSession session
            set session.revokedAt = :revokedAt, session.updatedAt = :revokedAt
            where session.user.id = :userId and session.revokedAt is null
            """)
    int revokeActiveSessions(
            @Param("userId") Long userId,
            @Param("revokedAt") Instant revokedAt
    );
}
