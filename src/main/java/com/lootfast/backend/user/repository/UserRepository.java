package com.lootfast.backend.user.repository;

import com.lootfast.backend.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByPublicId(java.util.UUID id);
    boolean existsByPublicId(java.util.UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.publicId=:id")
    Optional<User> findByPublicIdForUpdate(java.util.UUID id);

    @Query("""
        select u from User u
        where (cast(:pattern as String) is null
          or lower(concat(u.firstName, ' ', u.lastName)) like :pattern escape '!'
          or lower(u.email) like :pattern escape '!'
          or u.phoneNumber like :pattern escape '!')
        and (:role is null or :role member of u.roles)
        and (:verificationStatus is null or u.status=:verificationStatus)
        and (:locked is null or (:locked=true and u.lockedAt is not null)
          or (:locked=false and u.lockedAt is null))
        """)
    org.springframework.data.domain.Page<User> searchAdmin(String pattern,
        com.lootfast.backend.user.entity.UserRole role,
        com.lootfast.backend.user.entity.UserStatus verificationStatus, Boolean locked,
        org.springframework.data.domain.Pageable pageable);


    boolean existsByEmailIgnoreCase(String email);

    boolean existsByPhoneNumber(String phoneNumber);

    Optional<User> findByEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = "roles")
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdWithRoles(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where lower(u.email) = lower(:email)")
    Optional<User> findByEmailForUpdate(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);
}
