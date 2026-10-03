package com.lootfast.backend.support.repository;

import com.lootfast.backend.support.entity.SupportTicket;
import com.lootfast.backend.support.entity.TicketCategory;
import com.lootfast.backend.support.entity.TicketStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {

  @EntityGraph(attributePaths = {"order", "merchant", "merchant.user"})
  Optional<SupportTicket> findByPublicIdAndReporterId(UUID publicId, Long reporterId);

  @EntityGraph(attributePaths = {"order", "merchant", "merchant.user"})
  Page<SupportTicket> findByReporterId(Long reporterId, Pageable pageable);

  @EntityGraph(attributePaths = {"order", "merchant", "merchant.user"})
  Optional<SupportTicket> findByPublicId(UUID publicId);

  @EntityGraph(attributePaths = {"order", "merchant", "merchant.user"})
  Page<SupportTicket> findByStatus(TicketStatus status, Pageable pageable);

  @EntityGraph(attributePaths = {"order", "merchant", "merchant.user"})
  @Query("select t from SupportTicket t")
  Page<SupportTicket> findAllWithRefs(Pageable pageable);

  boolean existsByOrderIdAndCategory(Long orderId, TicketCategory category);
}
