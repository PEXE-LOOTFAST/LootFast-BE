package com.lootfast.backend.support.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Lý do/quyết định của Admin khi giải quyết hoặc từ chối ticket (lưu vào lịch sử). */
public record TicketDecisionRequest(@NotBlank @Size(max = 2000) String note) {}
