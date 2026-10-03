package com.lootfast.backend.support;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.support.dto.TicketDetailResponse;
import com.lootfast.backend.support.dto.TicketResponse;
import com.lootfast.backend.support.entity.SupportTicket;
import com.lootfast.backend.support.entity.TicketCategory;
import com.lootfast.backend.support.entity.TicketEvent;
import com.lootfast.backend.support.entity.TicketEventAction;
import com.lootfast.backend.support.entity.TicketOrigin;
import com.lootfast.backend.support.entity.TicketStatus;
import com.lootfast.backend.support.repository.SupportTicketRepository;
import com.lootfast.backend.support.repository.TicketEventRepository;
import com.lootfast.backend.support.service.AdminTicketService;
import com.lootfast.backend.user.entity.User;
import com.lootfast.backend.user.entity.UserRole;
import com.lootfast.backend.user.entity.UserStatus;
import com.lootfast.backend.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class AdminTicketServiceTests {
  private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");

  @Autowired UserRepository users;
  @Autowired SupportTicketRepository tickets;
  @Autowired TicketEventRepository events;
  @Autowired AdminTicketService service;

  @MockitoBean Clock clock;

  User admin;
  User buyer;

  @BeforeEach
  void setup() {
    when(clock.instant()).thenReturn(NOW);
    admin = user("Admin", UserRole.ADMIN);
    buyer = user("Buyer", UserRole.BUYER);
  }

  @Test
  void reviewThenResolveRecordsImmutableHistory() {
    UUID id = openTicket();

    service.review(adminId(), id);
    TicketResponse resolved = service.resolve(adminId(), id, "Đã liên hệ cửa hàng, hoàn tất");

    assertEquals(TicketStatus.RESOLVED, resolved.status());
    assertEquals("Đã liên hệ cửa hàng, hoàn tất", resolved.resolutionNote());

    TicketDetailResponse detail = service.getDetail(id);
    assertEquals(3, detail.history().size()); // CREATED, STATUS_CHANGED, RESOLVED
    assertEquals(TicketEventAction.CREATED, detail.history().get(0).action());
    assertEquals(TicketEventAction.STATUS_CHANGED, detail.history().get(1).action());
    assertEquals(TicketEventAction.RESOLVED, detail.history().get(2).action());
    assertEquals("Đã liên hệ cửa hàng, hoàn tất", detail.history().get(2).note());
  }

  @Test
  void rejectsInvalidTransition() {
    UUID id = openTicket(); // OPEN -> CLOSED không hợp lệ

    ApiException e = assertThrows(ApiException.class, () -> service.close(adminId(), id));
    assertEquals(ErrorCode.INVALID_TICKET_TRANSITION, e.getErrorCode());
  }

  @Test
  void rejectSetsStatusAndNote() {
    UUID id = openTicket();

    TicketResponse rejected = service.reject(adminId(), id, "Không đủ bằng chứng");

    assertEquals(TicketStatus.REJECTED, rejected.status());
    assertEquals("Không đủ bằng chứng", rejected.resolutionNote());
  }

  @Test
  void listFiltersByStatus() {
    openTicket();
    UUID toReject = openTicket();
    service.reject(adminId(), toReject, "x");

    assertEquals(1, service.list(TicketStatus.OPEN, 0, 20).totalElements());
    assertEquals(1, service.list(TicketStatus.REJECTED, 0, 20).totalElements());
    assertEquals(2, service.list(null, 0, 20).totalElements());
  }

  @Test
  void detailUnknownTicketFails() {
    ApiException e =
        assertThrows(ApiException.class, () -> service.getDetail(UUID.randomUUID()));
    assertEquals(ErrorCode.TICKET_NOT_FOUND, e.getErrorCode());
  }

  private UUID openTicket() {
    SupportTicket ticket =
        tickets.saveAndFlush(
            new SupportTicket(
                buyer, TicketOrigin.BUYER, TicketCategory.OTHER, null, null, "title", "desc", NOW));
    events.save(new TicketEvent(ticket, buyer, TicketEventAction.CREATED, null, NOW));
    return ticket.getPublicId();
  }

  private String adminId() {
    return admin.getId().toString();
  }

  private User user(String first, UserRole role) {
    return users.save(
        new User(
            first, "T", UUID.randomUUID() + "@test.local",
            null, null, role, UserStatus.VERIFIED));
  }
}
