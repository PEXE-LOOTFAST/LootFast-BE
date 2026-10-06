package com.lootfast.backend.content.service;

import com.lootfast.backend.common.exception.*;
import com.lootfast.backend.user.entity.*;
import com.lootfast.backend.user.repository.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ContentAccess {
  private final UserRepository users;

  public ContentAccess(UserRepository users) {
    this.users = users;
  }

  @Transactional(readOnly = true)
  public User admin(String subject) {
    long id;
    try {
      id = Long.parseLong(subject);
    } catch (RuntimeException e) {
      throw new ApiException(ErrorCode.ACCESS_DENIED);
    }
    User u =
        users.findByIdWithRoles(id).orElseThrow(() -> new ApiException(ErrorCode.ACCESS_DENIED));
    if (!u.hasRole(UserRole.ADMIN) || u.isLocked() || u.getStatus() != UserStatus.VERIFIED)
      throw new ApiException(ErrorCode.ACCESS_DENIED);
    return u;
  }

  public static org.springframework.data.domain.Pageable page(int page, int size, String... sort) {
    if (page < 0 || size < 1 || size > 50 || (long) page * size > Integer.MAX_VALUE)
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "Page size must be 1 to 50");
    return org.springframework.data.domain.PageRequest.of(
        page, size, org.springframework.data.domain.Sort.by(sort));
  }

  public static void revision(long actual, long expected) {
    if (actual != expected)
      throw new ApiException(ErrorCode.CONTENT_CONFLICT, "Content changed; reload before editing");
  }

  public static ApiException missing() {
    return new ApiException(ErrorCode.CONTENT_NOT_FOUND, "Content was not found");
  }
}
