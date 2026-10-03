package com.lootfast.backend.support.dto;

import com.lootfast.backend.support.entity.SupportTicket;
import com.lootfast.backend.support.entity.TicketCategory;
import com.lootfast.backend.support.entity.TicketOrigin;
import com.lootfast.backend.support.entity.TicketStatus;
import java.time.Instant;
import java.util.UUID;

public record TicketResponse(
    UUID ticketId,
    TicketOrigin origin,
    TicketCategory category,
    UUID orderId,
    UUID storeId,
    String title,
    String description,
    String evidenceImageUrl,
    TicketStatus status,
    String resolutionNote,
    Instant createdAt) {
  public static TicketResponse from(SupportTicket t) {
    return new TicketResponse(
        t.getPublicId(),
        t.getOrigin(),
        t.getCategory(),
        t.getOrder() == null ? null : t.getOrder().getPublicId(),
        t.getMerchant() == null ? null : t.getMerchant().getUser().getPublicId(),
        t.getTitle(),
        t.getDescription(),
        t.getEvidenceImageUrl(),
        t.getStatus(),
        t.getResolutionNote(),
        t.getCreatedAt());
  }
}
