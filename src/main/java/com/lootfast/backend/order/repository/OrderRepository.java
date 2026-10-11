package com.lootfast.backend.order.repository;

import com.lootfast.backend.order.entity.*;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface OrderRepository extends JpaRepository<CustomerOrder, Long> {
  @Query("""
      select o.publicId from CustomerOrder o
      left join NotificationPreference p on p.user.id = o.buyer.id
      where o.status = com.lootfast.backend.order.entity.OrderStatus.RESERVED
        and o.pickupStart > :now and o.pickupStart <= :until
        and coalesce(p.pickupRemindersEnabled, true) = true
        and not exists (select n.id from UserNotification n
          where n.recipient.id = o.buyer.id and n.orderPublicId = o.publicId
            and n.type = com.lootfast.backend.notification.entity.NotificationType.PICKUP_REMINDER)
      order by o.pickupStart, o.id
      """)
  List<UUID> findPickupReminderCandidates(Instant now, Instant until, Pageable pageable);

  interface ImpactTotals {
    Long getCompletedOrders();

    Long getRescuedPortions();

    BigDecimal getTotalSavings();
  }

  Optional<CustomerOrder> findByBuyerIdAndReservationKey(Long buyerId, UUID key);

  long countByBuyerIdAndStatus(Long buyerId, OrderStatus status);

  boolean existsByMerchantIdAndStatus(Long merchantId, OrderStatus status);

  long countByMerchantIdAndStatus(Long merchantId, OrderStatus status);

  long countByMerchantIdAndStatusIn(Long merchantId, Collection<OrderStatus> statuses);

  @Query(
      """
      select count(o) as completedOrders,
             sum(o.quantity) as rescuedPortions,
             sum((o.originalUnitPrice - o.unitPrice) * o.quantity) as totalSavings
      from CustomerOrder o
      where o.buyer.id = :buyerId
        and o.status = com.lootfast.backend.order.entity.OrderStatus.COMPLETED
      """)
  ImpactTotals summarizeCompletedImpact(Long buyerId);

  @Query(
      "select coalesce(sum(o.quantity),0) from CustomerOrder o where o.buyer.id=:buyerId and"
          + " o.deal.publicId=:dealId and"
          + " o.status=com.lootfast.backend.order.entity.OrderStatus.RESERVED")
  long reservedQuantity(Long buyerId, UUID dealId);

  @EntityGraph(attributePaths = {"deal", "merchant", "merchant.user", "buyer"})
  Optional<CustomerOrder> findByPublicIdAndBuyerId(UUID publicId, Long buyerId);

  @EntityGraph(attributePaths = {"deal", "merchant", "merchant.user", "buyer"})
  @Query(
      "select o from CustomerOrder o where o.merchant.user.id=:merchantUserId"
          + " and o.pickupStart>=:pickupFrom and o.pickupStart<:pickupTo")
  Page<CustomerOrder> findMerchantPickupOrders(
      Long merchantUserId, Instant pickupFrom, Instant pickupTo, Pageable pageable);

  @EntityGraph(attributePaths = {"deal", "merchant", "merchant.user", "buyer"})
  @Query(
      "select o from CustomerOrder o where o.merchant.user.id=:merchantUserId"
          + " and o.pickupStart>=:pickupFrom and o.pickupStart<:pickupTo and o.status=:status")
  Page<CustomerOrder> findMerchantPickupOrdersByStatus(
      Long merchantUserId,
      Instant pickupFrom,
      Instant pickupTo,
      OrderStatus status,
      Pageable pageable);

  @EntityGraph(attributePaths = {"deal", "merchant", "merchant.user", "buyer"})
  Page<CustomerOrder> findByBuyerId(Long buyerId, Pageable pageable);

  @EntityGraph(attributePaths = {"deal", "merchant", "merchant.user", "buyer"})
  Page<CustomerOrder> findByBuyerIdAndStatus(Long buyerId, OrderStatus status, Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
  @Query("select o from CustomerOrder o where o.publicId=:publicId")
  Optional<CustomerOrder> findForUpdate(UUID publicId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
  @Query(
      "select o from CustomerOrder o join fetch o.deal join fetch o.merchant m"
          + " join fetch m.user join fetch o.buyer where m.user.id=:merchantUserId"
          + " and o.pickupCode=:pickupCode")
  Optional<CustomerOrder> findMerchantOrderByPickupCodeForUpdate(
      Long merchantUserId, String pickupCode);

  @Query(
      "select o.publicId from CustomerOrder o where"
          + " o.status=com.lootfast.backend.order.entity.OrderStatus.RESERVED"
          + " and o.pickupTo < :cutoff")
  List<UUID> findNoShowCandidates(Instant cutoff);

  @Query(
      "select o.publicId from CustomerOrder o where"
          + " o.paymentMethod=com.lootfast.backend.order.entity.PaymentMethod.BANK_TRANSFER"
          + " and o.paymentStatus=com.lootfast.backend.order.entity.PaymentStatus.REVIEW_REQUIRED"
          + " and o.finalizedAt is not null and o.finalizedAt <= :cutoff")
  List<UUID> findUnconfirmedTransferCandidates(Instant cutoff);

  @Query(
      "select o.finalizedAt from CustomerOrder o where o.buyer.id=:buyerId and"
          + " o.status=com.lootfast.backend.order.entity.OrderStatus.NO_SHOW and"
          + " o.finalizedAt >= :since order by o.finalizedAt desc")
  List<Instant> recentNoShowTimes(Long buyerId, Instant since);
}
