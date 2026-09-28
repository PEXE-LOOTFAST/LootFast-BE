package com.lootfast.backend.order.repository;

import com.lootfast.backend.order.entity.*;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface OrderRepository extends JpaRepository<CustomerOrder, Long> {
  Optional<CustomerOrder> findByBuyerIdAndReservationKey(Long buyerId, UUID key);

  long countByBuyerIdAndStatus(Long buyerId, OrderStatus status);

  boolean existsByMerchantIdAndStatus(Long merchantId, OrderStatus status);

  @Query(
      "select coalesce(sum(o.quantity),0) from CustomerOrder o where o.buyer.id=:buyerId and"
          + " o.deal.publicId=:dealId and"
          + " o.status=com.lootfast.backend.order.entity.OrderStatus.RESERVED")
  long reservedQuantity(Long buyerId, UUID dealId);

  @EntityGraph(attributePaths = {"deal", "merchant", "merchant.user", "buyer"})
  Optional<CustomerOrder> findByPublicIdAndBuyerId(UUID publicId, Long buyerId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
  @Query("select o from CustomerOrder o where o.publicId=:publicId")
  Optional<CustomerOrder> findForUpdate(UUID publicId);

  @Query(
      "select o.publicId from CustomerOrder o where"
          + " o.status=com.lootfast.backend.order.entity.OrderStatus.RESERVED"
          + " and o.pickupEnd < :cutoff")
  List<UUID> findNoShowCandidates(Instant cutoff);
}
