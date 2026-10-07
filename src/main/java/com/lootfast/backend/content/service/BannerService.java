package com.lootfast.backend.content.service;

import com.lootfast.backend.common.exception.*;
import com.lootfast.backend.common.response.PagedResponse;
import com.lootfast.backend.content.dto.ContentRequests.*;
import com.lootfast.backend.content.dto.ContentResponses.*;
import com.lootfast.backend.content.entity.Banner;
import com.lootfast.backend.content.repository.BannerRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BannerService {
  private final BannerRepository banners;
  private final ContentAccess access;
  private final Clock clock;

  public BannerService(BannerRepository banners, ContentAccess access, Clock clock) {
    this.banners = banners;
    this.access = access;
    this.clock = clock;
  }

  @Transactional(readOnly = true, timeout = 5)
  public PagedResponse<BannerView> list(boolean admin, String subject, int page, int size) {
    if (admin) access.admin(subject);
    var p = ContentAccess.page(page, size, "sortOrder", "id");
    return PagedResponse.from(
        (admin ? banners.findAll(p) : banners.visible(clock.instant(), p)).map(BannerView::from));
  }

  @Transactional(timeout = 5)
  public BannerView write(String subject, UUID id, BannerWrite r) {
    var actor = access.admin(subject);
    Banner b = id == null ? new Banner() : banners.lock(id).orElseThrow(ContentAccess::missing);
    if (id != null) ContentAccess.revision(b.getRevision(), r.expectedRevision());
    if (!r.targetPath()
        .matches(
            "/|/deals|/(deals|stores)/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
      throw new ApiException(
          ErrorCode.VALIDATION_FAILED, "Target must be /, /deals, /deals/{UUID} or /stores/{UUID}");
    if ((r.startsAt() == null) != (r.endsAt() == null)
        || (r.startsAt() != null && !r.startsAt().isBefore(r.endsAt())))
      throw new ApiException(
          ErrorCode.VALIDATION_FAILED, "Provide both schedule times with start before end");
    if (r.enabled() && b.getImageUrl() == null)
      throw new ApiException(
          ErrorCode.VALIDATION_FAILED, "Upload an image before enabling the banner");
    b.configure(
        r.title().trim(),
        r.altText().trim(),
        r.targetPath(),
        r.enabled(),
        r.sortOrder(),
        r.startsAt(),
        r.endsAt(),
        clock.instant(),
        actor.getPublicId());
    return BannerView.from(banners.saveAndFlush(b));
  }
}
