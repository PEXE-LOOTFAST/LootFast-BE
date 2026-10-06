package com.lootfast.backend.deal.repository;

import com.lootfast.backend.deal.entity.Deal;
import com.lootfast.backend.deal.entity.DealStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface DealRepository extends JpaRepository<Deal, Long> {
    Optional<Deal> findByPublicId(UUID publicId);

    @Query("""
            select deal
            from Deal deal
            where deal.publicId = :publicId
              and deal.merchant.user.id = :merchantUserId
            """)
    Optional<Deal> findOwned(
            @Param("publicId") UUID publicId,
            @Param("merchantUserId") Long merchantUserId
    );

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.QueryHints(
            @jakarta.persistence.QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")
    )
    @Query("""
            select deal
            from Deal deal
            where deal.publicId = :publicId
              and deal.merchant.user.id = :merchantUserId
            """)
    Optional<Deal> findOwnedForUpdate(
            @Param("publicId") UUID publicId,
            @Param("merchantUserId") Long merchantUserId
    );

    @Query(
            value = """
                    select deal
                    from Deal deal
                    where deal.merchant.user.id = :merchantUserId
                    """,
            countQuery = """
                    select count(deal)
                    from Deal deal
                    where deal.merchant.user.id = :merchantUserId
                    """
    )
    Page<Deal> findMerchantDeals(
            @Param("merchantUserId") Long merchantUserId,
            Pageable pageable
    );

    @Query("select d.merchant.user.id from Deal d where d.publicId=:publicId")
    Optional<Long> findMerchantUserId(UUID publicId);
    @Query("""
        select count(d) from Deal d where d.merchant.id=:merchantId and d.publicId<>:dealPublicId
        and d.status in :statuses and d.availableFrom>=:dayStart and d.availableFrom<:dayEnd
        and (d.availableFrom<>:saleStart or d.availableUntil<>:saleEnd
             or d.pickupStart<>:pickupStart or d.pickupEnd<>:pickupEnd)
        """)
    long countScheduleConflicts(Long merchantId, UUID dealPublicId, Set<DealStatus> statuses, Instant dayStart, Instant dayEnd,
                               Instant saleStart, Instant saleEnd, Instant pickupStart, Instant pickupEnd);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.QueryHints(@jakarta.persistence.QueryHint(name="jakarta.persistence.lock.timeout", value="3000"))
    @Query("select deal from Deal deal where deal.publicId = :publicId")
    Optional<Deal> findReservationForUpdate(@Param("publicId") UUID publicId);


    String GLOBAL_FEED_FILTER = """
            where deal.status = :status
              and deal.remainingQuantity > 0
              and deal.availableFrom <= :now
              and deal.availableUntil > :now
              and deal.pickupEnd > :now
              and merchant.storeName is not null
              and merchant.latitude is not null
              and merchant.longitude is not null
              and (cast(:categoryCode as String) is null or deal.categoryCode = :categoryCode)
              and (cast(:minDiscountPercent as Integer) is null
                   or (deal.originalPrice - deal.discountedPrice) * 100 >= deal.originalPrice * :minDiscountPercent)
              and (cast(:pickupFrom as timestamp) is null or deal.pickupEnd > :pickupFrom)
              and (cast(:pickupTo as timestamp) is null or deal.pickupStart < :pickupTo)
              and (cast(:searchTerm as String) is null
                   or deal.searchName like concat('%', cast(:searchTerm as String), '%')
                   or merchant.searchName like concat('%', cast(:searchTerm as String), '%'))
            """;

    @Query(value = "select deal from Deal deal join fetch deal.merchant merchant join fetch merchant.user " + GLOBAL_FEED_FILTER,
            countQuery = "select count(deal) from Deal deal join deal.merchant merchant " + GLOBAL_FEED_FILTER)
    Page<Deal> findGlobalFeed(
            @Param("status") DealStatus status,
            @Param("now") Instant now,
            @Param("categoryCode") String categoryCode,
            @Param("minDiscountPercent") Integer minDiscountPercent,
            @Param("pickupFrom") Instant pickupFrom,
            @Param("pickupTo") Instant pickupTo,
            @Param("searchTerm") String searchTerm,
            Pageable pageable
    );

    // PostgreSQL treats repeated named parameters as separate bind slots. Cast
    // nullable guards explicitly; otherwise IS NULL / LIKE can be unknown/bytea.
    // Timestamp casts are only in null guards; comparisons retain Instant binding.
    @Query("""
            select deal
            from Deal deal
            join fetch deal.merchant merchant
            join fetch merchant.user merchantUser
            where deal.status = :status
              and deal.remainingQuantity > 0
              and deal.availableFrom <= :now
              and deal.availableUntil > :now
              and deal.pickupEnd > :now
              and (cast(:categoryCode as String) is null or deal.categoryCode = :categoryCode)
              and (cast(:minDiscountPercent as Integer) is null
                   or (deal.originalPrice - deal.discountedPrice) * 100
                      >= deal.originalPrice * :minDiscountPercent)
              and (cast(:pickupFrom as timestamp) is null or deal.pickupEnd > :pickupFrom)
              and (cast(:pickupTo as timestamp) is null or deal.pickupStart < :pickupTo)
              and (cast(:searchTerm as String) is null
                   or deal.searchName like concat('%', cast(:searchTerm as String), '%')
                   or merchant.searchName like concat('%', cast(:searchTerm as String), '%'))
              and merchant.storeName is not null
              and merchant.latitude between :minLatitude and :maxLatitude
              and (
                    (:crossesDateLine = false
                        and merchant.longitude between :minLongitude and :maxLongitude)
                    or (:crossesDateLine = true
                        and (merchant.longitude >= :minLongitude
                            or merchant.longitude <= :maxLongitude))
              )
            """)
    List<Deal> findFeedCandidates(
            @Param("status") DealStatus status,
            @Param("now") Instant now,
            @Param("minLatitude") BigDecimal minLatitude,
            @Param("maxLatitude") BigDecimal maxLatitude,
            @Param("minLongitude") BigDecimal minLongitude,
            @Param("maxLongitude") BigDecimal maxLongitude,
            @Param("crossesDateLine") boolean crossesDateLine,
            @Param("categoryCode") String categoryCode,
            @Param("minDiscountPercent") Integer minDiscountPercent,
            @Param("pickupFrom") Instant pickupFrom,
            @Param("pickupTo") Instant pickupTo,
            @Param("searchTerm") String searchTerm
    );

    @Query("""
            select deal
            from Deal deal
            join fetch deal.merchant merchant
            join fetch merchant.user merchantUser
            where deal.publicId = :publicId
              and deal.status in :statuses
              and deal.availableFrom <= :now
              and deal.availableUntil > :now
              and deal.pickupEnd > :now
              and merchant.storeName is not null
              and merchant.latitude is not null
              and merchant.longitude is not null
            """)
    Optional<Deal> findPublicDeal(
            @Param("publicId") UUID publicId,
            @Param("statuses") Set<DealStatus> statuses,
            @Param("now") Instant now
    );

    @Query(
            value = """
                    select deal
                    from Deal deal
                    join fetch deal.merchant merchant
                    join fetch merchant.user merchantUser
                    where merchant.id = :merchantProfileId
                      and deal.status = :status
                      and deal.remainingQuantity > 0
                      and deal.availableFrom <= :now
                      and deal.availableUntil > :now
                      and deal.pickupEnd > :now
                    """,
            countQuery = """
                    select count(deal)
                    from Deal deal
                    where deal.merchant.id = :merchantProfileId
                      and deal.status = :status
                      and deal.remainingQuantity > 0
                      and deal.availableFrom <= :now
                      and deal.availableUntil > :now
                      and deal.pickupEnd > :now
                    """
    )
    Page<Deal> findPublicStoreDeals(
            @Param("merchantProfileId") Long merchantProfileId,
            @Param("status") DealStatus status,
            @Param("now") Instant now,
            Pageable pageable
    );
}
