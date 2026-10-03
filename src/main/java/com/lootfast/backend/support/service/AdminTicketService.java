package com.lootfast.backend.support.service;

import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.common.response.PagedResponse;
import com.lootfast.backend.support.dto.TicketDetailResponse;
import com.lootfast.backend.support.dto.TicketEventResponse;
import com.lootfast.backend.support.dto.TicketResponse;
import com.lootfast.backend.support.entity.SupportTicket;
import com.lootfast.backend.support.entity.TicketEvent;
import com.lootfast.backend.support.entity.TicketEventAction;
import com.lootfast.backend.support.entity.TicketStatus;
import com.lootfast.backend.support.repository.SupportTicketRepository;
import com.lootfast.backend.support.repository.TicketEventRepository;
import com.lootfast.backend.user.entity.User;
import com.lootfast.backend.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin phân loại, quyết định và đóng ticket (070); mọi thao tác ghi lịch sử bất biến (074). */
@Service
public class AdminTicketService {
  private final SupportTicketRepository tickets;
  private final TicketEventRepository events;
  private final UserRepository users;
  private final Clock clock;

  public AdminTicketService(
      SupportTicketRepository tickets,
      TicketEventRepository events,
      UserRepository users,
      Clock clock) {
    this.tickets = tickets;
    this.events = events;
    this.users = users;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public PagedResponse<TicketResponse> list(TicketStatus status, int page, int size) {
    var pageable =
        PageRequest.of(page, size, Sort.by("createdAt").descending().and(Sort.by("id").descending()));
    Page<SupportTicket> result =
        status == null ? tickets.findAllWithRefs(pageable) : tickets.findByStatus(status, pageable);
    return PagedResponse.from(result.map(TicketResponse::from));
  }

  @Transactional(readOnly = true)
  public TicketDetailResponse getDetail(UUID ticketId) {
    SupportTicket ticket = requireTicket(ticketId);
    var history =
        events.findByTicketIdOrderByCreatedAtAscIdAsc(ticket.getId()).stream()
            .map(TicketEventResponse::from)
            .toList();
    return new TicketDetailResponse(TicketResponse.from(ticket), history);
  }

  @Transactional
  public TicketResponse review(String adminSubject, UUID ticketId) {
    SupportTicket ticket = requireTransitionable(ticketId, TicketStatus.IN_REVIEW);
    Instant now = clock.instant();
    ticket.markInReview(now);
    record(ticket, adminSubject, TicketEventAction.STATUS_CHANGED, null, now);
    return TicketResponse.from(ticket);
  }

  @Transactional
  public TicketResponse resolve(String adminSubject, UUID ticketId, String note) {
    SupportTicket ticket = requireTransitionable(ticketId, TicketStatus.RESOLVED);
    Instant now = clock.instant();
    ticket.resolve(note, now);
    record(ticket, adminSubject, TicketEventAction.RESOLVED, note, now);
    return TicketResponse.from(ticket);
  }

  @Transactional
  public TicketResponse reject(String adminSubject, UUID ticketId, String note) {
    SupportTicket ticket = requireTransitionable(ticketId, TicketStatus.REJECTED);
    Instant now = clock.instant();
    ticket.reject(note, now);
    record(ticket, adminSubject, TicketEventAction.REJECTED, note, now);
    return TicketResponse.from(ticket);
  }

  @Transactional
  public TicketResponse close(String adminSubject, UUID ticketId) {
    SupportTicket ticket = requireTransitionable(ticketId, TicketStatus.CLOSED);
    Instant now = clock.instant();
    ticket.close(now);
    record(ticket, adminSubject, TicketEventAction.CLOSED, null, now);
    return TicketResponse.from(ticket);
  }

  private SupportTicket requireTicket(UUID ticketId) {
    return tickets
        .findByPublicId(ticketId)
        .orElseThrow(() -> new ApiException(ErrorCode.TICKET_NOT_FOUND, "Ticket was not found"));
  }

  private SupportTicket requireTransitionable(UUID ticketId, TicketStatus target) {
    SupportTicket ticket = requireTicket(ticketId);
    if (!ticket.getStatus().canTransitionTo(target))
      throw new ApiException(
          ErrorCode.INVALID_TICKET_TRANSITION,
          "Cannot move ticket from " + ticket.getStatus() + " to " + target);
    return ticket;
  }

  private void record(
      SupportTicket ticket,
      String adminSubject,
      TicketEventAction action,
      String note,
      Instant now) {
    events.save(new TicketEvent(ticket, admin(adminSubject), action, note, now));
  }

  private User admin(String subject) {
    try {
      return users.getReferenceById(Long.valueOf(subject));
    } catch (NumberFormatException | NullPointerException e) {
      throw new ApiException(ErrorCode.USER_NOT_FOUND, "Admin is unavailable");
    }
  }
}
