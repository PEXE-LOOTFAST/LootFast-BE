package com.lootfast.backend.merchant.sanction.service;

import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.common.response.PagedResponse;
import com.lootfast.backend.merchant.entity.MerchantProfile;
import com.lootfast.backend.merchant.repository.MerchantProfileRepository;
import com.lootfast.backend.merchant.sanction.dto.MerchantSanctionResponse;
import com.lootfast.backend.merchant.sanction.entity.MerchantSanction;
import com.lootfast.backend.merchant.sanction.entity.SanctionType;
import com.lootfast.backend.merchant.sanction.repository.MerchantSanctionRepository;
import com.lootfast.backend.user.entity.User;
import com.lootfast.backend.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin cảnh cáo / đình chỉ cửa hàng (FR-TR-05). Lịch sử lưu bất biến, đình chỉ tự gỡ khi hết hạn. */
@Service
public class MerchantSanctionService {
  private static final long SECONDS_PER_DAY = 24 * 3600;

  private final MerchantProfileRepository merchants;
  private final MerchantSanctionRepository sanctions;
  private final UserRepository users;
  private final Clock clock;

  public MerchantSanctionService(
      MerchantProfileRepository merchants,
      MerchantSanctionRepository sanctions,
      UserRepository users,
      Clock clock) {
    this.merchants = merchants;
    this.sanctions = sanctions;
    this.users = users;
    this.clock = clock;
  }

  @Transactional
  public MerchantSanctionResponse warn(String adminSubject, UUID storeId, String reason) {
    MerchantProfile merchant = requireStore(storeId);
    Instant now = clock.instant();
    MerchantSanction sanction =
        sanctions.saveAndFlush(MerchantSanction.warning(merchant, admin(adminSubject), reason, now));
    return MerchantSanctionResponse.from(sanction, now);
  }

  @Transactional
  public MerchantSanctionResponse suspend(
      String adminSubject, UUID storeId, String reason, int durationDays) {
    MerchantProfile merchant = requireStoreForUpdate(storeId);
    Instant now = clock.instant();
    Instant until = now.plusSeconds(durationDays * SECONDS_PER_DAY);
    MerchantSanction sanction =
        sanctions.saveAndFlush(
            MerchantSanction.suspension(merchant, admin(adminSubject), reason, now, until));
    merchant.suspendUntil(until);
    return MerchantSanctionResponse.from(sanction, now);
  }

  @Transactional
  public MerchantSanctionResponse lift(String adminSubject, UUID sanctionId) {
    MerchantSanction sanction =
        sanctions
            .findByPublicId(sanctionId)
            .orElseThrow(
                () ->
                    new ApiException(
                        ErrorCode.MERCHANT_SANCTION_NOT_FOUND, "Sanction was not found"));
    if (sanction.getLiftedAt() != null)
      throw new ApiException(ErrorCode.SANCTION_ALREADY_LIFTED, "Sanction was already lifted");
    Instant now = clock.instant();
    sanction.lift(admin(adminSubject), now);
    if (sanction.getSanctionType() == SanctionType.SUSPENSION) {
      merchants
          .findByUserPublicIdForUpdate(sanction.getMerchant().getUser().getPublicId())
          .ifPresent(MerchantProfile::liftSuspension);
    }
    return MerchantSanctionResponse.from(sanction, now);
  }

  @Transactional(readOnly = true)
  public PagedResponse<MerchantSanctionResponse> list(UUID storeId, int page, int size) {
    MerchantProfile merchant = requireStore(storeId);
    Instant now = clock.instant();
    var pageable =
        PageRequest.of(page, size, Sort.by("createdAt").descending().and(Sort.by("id").descending()));
    return PagedResponse.from(
        sanctions.findByMerchantId(merchant.getId(), pageable).map(s -> MerchantSanctionResponse.from(s, now)));
  }

  /** Dọn cờ đình chỉ đã hết hạn (gọi bởi scheduler). Trả về số cửa hàng được gỡ. */
  @Transactional
  public int clearExpiredSuspensions() {
    return merchants.clearExpiredSuspensions(clock.instant());
  }

  private MerchantProfile requireStore(UUID storeId) {
    return merchants
        .findByUserPublicId(storeId)
        .orElseThrow(
            () -> new ApiException(ErrorCode.MERCHANT_PROFILE_NOT_FOUND, "Store was not found"));
  }

  private MerchantProfile requireStoreForUpdate(UUID storeId) {
    return merchants
        .findByUserPublicIdForUpdate(storeId)
        .orElseThrow(
            () -> new ApiException(ErrorCode.MERCHANT_PROFILE_NOT_FOUND, "Store was not found"));
  }

  private User admin(String subject) {
    try {
      return users.getReferenceById(Long.valueOf(subject));
    } catch (NumberFormatException | NullPointerException e) {
      throw new ApiException(ErrorCode.USER_NOT_FOUND, "Admin is unavailable");
    }
  }
}
