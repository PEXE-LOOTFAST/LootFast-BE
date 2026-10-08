package com.lootfast.backend.support.dto;

import com.lootfast.backend.support.entity.TicketEvent;
import com.lootfast.backend.support.entity.TicketEventAction;
import java.time.Instant;

public record TicketEventResponse(TicketEventAction action, String note, Instant createdAt) {
  public static TicketEventResponse from(TicketEvent e) {
    return new TicketEventResponse(e.getAction(), e.getNote(), e.getCreatedAt());
  }
}
