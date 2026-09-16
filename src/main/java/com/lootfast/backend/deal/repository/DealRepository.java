package com.lootfast.backend.deal.repository;

import com.lootfast.backend.deal.entity.Deal;
import com.lootfast.backend.deal.entity.DealStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

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
            @Param("crossesDateLine") boolean crossesDateLine
    );
}
