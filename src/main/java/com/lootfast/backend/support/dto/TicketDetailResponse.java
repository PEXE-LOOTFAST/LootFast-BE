package com.lootfast.backend.support.dto;

import java.util.List;

/** Ticket kèm toàn bộ lịch sử xử lý bất biến (FR-AD-04). */
public record TicketDetailResponse(TicketResponse ticket, List<TicketEventResponse> history) {}
