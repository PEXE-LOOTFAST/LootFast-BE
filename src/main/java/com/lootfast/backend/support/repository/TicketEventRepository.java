package com.lootfast.backend.support.repository;

import com.lootfast.backend.support.entity.TicketEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketEventRepository extends JpaRepository<TicketEvent, Long> {

  List<TicketEvent> findByTicketIdOrderByCreatedAtAscIdAsc(Long ticketId);
}
