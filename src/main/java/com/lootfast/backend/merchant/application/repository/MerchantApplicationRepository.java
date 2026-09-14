package com.lootfast.backend.merchant.application.repository;

import com.lootfast.backend.merchant.application.entity.MerchantApplication;
import com.lootfast.backend.merchant.application.entity.MerchantApplicationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface MerchantApplicationRepository
        extends JpaRepository<MerchantApplication, Long> {

    Optional<MerchantApplication> findByUserId(Long userId);

    Optional<MerchantApplication> findByPublicId(UUID publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from MerchantApplication a where a.user.id = :userId")
    Optional<MerchantApplication> findByUserIdForUpdate(@Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from MerchantApplication a where a.publicId = :publicId")
    Optional<MerchantApplication> findByPublicIdForUpdate(@Param("publicId") UUID publicId);

    @Query("select a.user.id from MerchantApplication a where a.publicId = :publicId")
    Optional<Long> findApplicantUserIdByPublicId(@Param("publicId") UUID publicId);

    @EntityGraph(attributePaths = {"user", "reviewedBy"})
    Page<MerchantApplication> findAllByStatus(
            MerchantApplicationStatus status,
            Pageable pageable
    );
}
