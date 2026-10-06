package com.lootfast.backend.user.admin.dto;
import com.lootfast.backend.user.entity.*;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
public record AdminUserResponse(UUID userId, String firstName, String lastName, String email,
    String phoneNumber, Set<UserRole> roles, UserStatus verificationStatus, boolean locked,
    Instant lockedAt, String lockReason, Instant createdAt) {
  public static AdminUserResponse from(User u) {
    return new AdminUserResponse(u.getPublicId(), u.getFirstName(), u.getLastName(), u.getEmail(),
        u.getPhoneNumber(), u.getRoles(), u.getStatus(), u.isLocked(), u.getLockedAt(), u.getLockReason(), u.getCreatedAt());
  }
}
