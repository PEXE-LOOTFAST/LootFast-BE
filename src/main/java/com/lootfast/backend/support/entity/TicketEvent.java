package com.lootfast.backend.support.entity;

import com.lootfast.backend.user.entity.User;
import jakarta.persistence.*;
import java.time.Instant;

/** Một dòng lịch sử bất biến của ticket (FR-AD-04). Chỉ tạo, không sửa. */
@Entity
@Table(name = "ticket_events")
public class TicketEvent {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "ticket_id", updatable = false)
  private SupportTicket ticket;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "actor_user_id", updatable = false)
  private User actor;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24, updatable = false)
  private TicketEventAction action;

  @Column(length = 2000, updatable = false)
  private String note;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected TicketEvent() {}

  public TicketEvent(
      SupportTicket ticket, User actor, TicketEventAction action, String note, Instant now) {
    this.ticket = ticket;
    this.actor = actor;
    this.action = action;
    this.note = note;
    this.createdAt = now;
  }

  public Long getId() {
    return id;
  }

  public TicketEventAction getAction() {
    return action;
  }

  public String getNote() {
    return note;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
