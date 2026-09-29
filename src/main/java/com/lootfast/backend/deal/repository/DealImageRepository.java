package com.lootfast.backend.deal.repository;

import com.lootfast.backend.deal.entity.DealImage;
import com.lootfast.backend.deal.entity.DealImageStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DealImageRepository extends JpaRepository<DealImage, Long> {

    Optional<DealImage> findByPublicId(UUID publicId);

    List<DealImage> findAllByDealIdAndStatusOrderByDisplayOrderAscIdAsc(
            Long dealId,
            DealImageStatus status
    );

    @Query("""
            select image
            from DealImage image
            where image.deal.id in :dealIds
              and image.status = :status
            order by image.deal.id, image.displayOrder, image.id
            """)
    List<DealImage> findAllActiveByDealIds(
            @Param("dealIds") Collection<Long> dealIds,
            @Param("status") DealImageStatus status
    );

    long countByDealIdAndStatus(Long dealId, DealImageStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select image
            from DealImage image
            join fetch image.deal deal
            join fetch deal.merchant merchant
            where image.publicId = :publicId
              and merchant.user.id = :merchantUserId
            """)
    Optional<DealImage> findOwnedForUpdate(
            @Param("publicId") UUID publicId,
            @Param("merchantUserId") Long merchantUserId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select image
            from DealImage image
            join fetch image.deal deal
            where image.publicId = :publicId
            """)
    Optional<DealImage> findByPublicIdForUpdate(@Param("publicId") UUID publicId);
}
