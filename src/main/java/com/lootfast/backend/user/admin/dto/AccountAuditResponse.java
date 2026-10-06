package com.lootfast.backend.user.admin.dto;
import com.lootfast.backend.user.admin.entity.AccountAudit;
import java.time.Instant;
import java.util.UUID;
public record AccountAuditResponse(UUID auditId, UUID userId, UUID adminId, String action,
    String reason, Instant createdAt) {
  public static AccountAuditResponse from(AccountAudit a) {
    return new AccountAuditResponse(a.getId(),a.getTargetPublicId(),a.getActorPublicId(),a.getAction(),a.getReason(),a.getCreatedAt());
  }
}
