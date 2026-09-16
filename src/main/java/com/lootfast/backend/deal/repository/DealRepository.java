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
              and (:categoryCode is null or deal.categoryCode = :categoryCode)
              and (:minDiscountPercent is null
                   or (deal.originalPrice - deal.discountedPrice) * 100
                      >= deal.originalPrice * :minDiscountPercent)
              and (:pickupFrom is null or deal.pickupEnd > :pickupFrom)
              and (:pickupTo is null or deal.pickupStart < :pickupTo)
              and (:searchTerm is null
                   or deal.searchName like concat('%', :searchTerm, '%')
                   or merchant.searchName like concat('%', :searchTerm, '%'))
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
