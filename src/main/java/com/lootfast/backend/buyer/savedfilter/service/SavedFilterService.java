package com.lootfast.backend.buyer.savedfilter.service;

import com.lootfast.backend.buyer.savedfilter.dto.*;
import com.lootfast.backend.buyer.savedfilter.repository.*;
import com.lootfast.backend.common.exception.*;
import com.lootfast.backend.common.response.PagedResponse;
import com.lootfast.backend.content.service.CategoryService;
import com.lootfast.backend.deal.dto.DealFeedItemResponse;
import jakarta.validation.Validator;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SavedFilterService {
  public static final int LIMIT = 10;
  private final BuyerPremiumAccess access;
  private final SavedFilterRepository filters;
  private final SavedFilterDealRepository deals;
  private final CategoryService categories;
  private final Validator validator;
  private final Clock clock;
  private final SavedFilterSearchRateLimiter limiter;
  private final TransactionTemplate snapshot;
  private final TransactionTemplate read;

  public SavedFilterService(
      BuyerPremiumAccess access,
      SavedFilterRepository filters,
      SavedFilterDealRepository deals,
      CategoryService categories,
      Validator validator,
      Clock clock,
      SavedFilterSearchRateLimiter limiter,
      PlatformTransactionManager tm) {
    this.access = access;
    this.filters = filters;
    this.deals = deals;
    this.categories = categories;
    this.validator = validator;
    this.clock = clock;
    this.limiter = limiter;
    read = new TransactionTemplate(tm);
    read.setReadOnly(true);
    read.setTimeout(5);
    snapshot = new TransactionTemplate(tm);
    snapshot.setReadOnly(true);
    snapshot.setTimeout(10);
    snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
  }

  public record Benefits(boolean savedFilters, int savedFilterLimit) {}

  @Transactional(readOnly = true, timeout = 5)
  public Benefits benefits(String subject) {
    return new Benefits(access.active(access.user(subject, false)), LIMIT);
  }

  @Transactional(readOnly = true, timeout = 5)
  public PagedResponse<SavedFilterResponse> list(String subject, int page, int size) {
    page(page, size);
    return filters.list(access.user(subject, false).getId(), page, size);
  }

  @Transactional(readOnly = true, timeout = 5)
  public SavedFilterResponse get(String subject, UUID id) {
    return owned(access.user(subject, false).getId(), id);
  }

  @Transactional(timeout = 5)
  public SavedFilterResponse create(String subject, SavedFilterCriteria criteria) {
    var c = validate(criteria);
    var u = access.user(subject, true);
    access.require(u);
    if (filters.count(u.getId()) >= LIMIT)
      throw new ApiException(
          ErrorCode.SAVED_FILTER_LIMIT_REACHED, "At most ten saved filters per buyer");
    if (c.categoryCode() != null) categories.requireEnabled(c.categoryCode());
    UUID id = UUID.randomUUID();
    filters.create(u.getId(), id, c, clock.instant());
    return owned(u.getId(), id);
  }

  @Transactional(timeout = 5)
  public SavedFilterResponse update(String subject, UUID id, UpdateSavedFilterRequest request) {
    if (request == null || request.expectedRevision() == null || request.expectedRevision() < 0)
      throw new ApiException(ErrorCode.VALIDATION_FAILED);
    var c = validate(request.criteria());
    var u = access.user(subject, true);
    access.require(u);
    var previous = owned(u.getId(), id);
    if (previous.revision() != request.expectedRevision())
      throw new ApiException(
          ErrorCode.SAVED_FILTER_CONFLICT, "Filter changed; reload its revision");
    if (c.categoryCode() != null) categories.requireEnabled(c.categoryCode());
    filters.update(u.getId(), id, c, clock.instant());
    return owned(u.getId(), id);
  }

  @Transactional(timeout = 5)
  public SavedFilterResponse notifications(String subject, UUID id, FilterNotificationRequest r) {
    if (r == null
        || r.enabled() == null
        || r.expectedRevision() == null
        || r.expectedRevision() < 0) throw new ApiException(ErrorCode.VALIDATION_FAILED);
    var u = access.user(subject, true);
    if (r.enabled()) access.require(u);
    var f = owned(u.getId(), id);
    if (f.revision() != r.expectedRevision())
      throw new ApiException(
          ErrorCode.SAVED_FILTER_CONFLICT, "Filter changed; reload its revision");
    if (f.notificationsEnabled() == r.enabled()) return f;
    if (r.enabled() && f.criteria().categoryCode() != null)
      categories.requireEnabled(f.criteria().categoryCode());
    filters.notifications(u.getId(), id, r.enabled(), clock.instant());
    return owned(u.getId(), id);
  }

  @Transactional(timeout = 5)
  public void delete(String subject, UUID id, long expectedRevision) {
    if (expectedRevision < 0) throw new ApiException(ErrorCode.VALIDATION_FAILED);
    var u = access.user(subject, true);
    var previous = owned(u.getId(), id);
    if (previous.revision() != expectedRevision)
      throw new ApiException(
          ErrorCode.SAVED_FILTER_CONFLICT, "Filter changed; reload its revision");
    filters.delete(u.getId(), id);
  }

  // The Redis request runs after the short precheck transaction closes, before opening the SQL
  // snapshot.
  public PagedResponse<DealFeedItemResponse> search(
      String subject, UUID id, LocalDate date, int page, int size) {
    page(page, size);
    LocalDate day =
        date == null ? LocalDate.ofInstant(clock.instant(), ZoneId.of("Asia/Ho_Chi_Minh")) : date;
    if (day.getYear() < 1 || day.getYear() > 9998)
      throw new ApiException(
          ErrorCode.INVALID_DEAL_FILTER, "Provide a date between years 1 and 9998");
    Long user =
        read.execute(
            s -> {
              var u = access.user(subject, false);
              access.require(u);
              owned(u.getId(), id);
              return u.getId();
            });
    limiter.requireAllowed(user);
    return snapshot.execute(
        s -> {
          var u = access.user(subject, false);
          access.require(u);
          return deals.search(owned(u.getId(), id).criteria(), day, clock.instant(), page, size);
        });
  }

  private SavedFilterResponse owned(long user, UUID id) {
    return filters
        .get(user, id)
        .orElseThrow(() -> new ApiException(ErrorCode.SAVED_FILTER_NOT_FOUND));
  }

  private static void page(int page, int size) {
    if (page < 0 || size < 1 || size > 50 || (long) page * size > Integer.MAX_VALUE)
      throw new ApiException(ErrorCode.VALIDATION_FAILED);
  }

  private SavedFilterCriteria validate(SavedFilterCriteria c) {
    if (c == null || !validator.validate(c).isEmpty())
      throw new ApiException(ErrorCode.VALIDATION_FAILED);
    if ((c.pickupFrom() == null) != (c.pickupTo() == null)
        || (c.pickupFrom() != null
            && (!c.pickupFrom().isBefore(c.pickupTo())
                || c.pickupFrom().getNano() != 0
                || c.pickupTo().getNano() != 0)))
      throw new ApiException(
          ErrorCode.INVALID_DEAL_FILTER,
          "Provide both pickup times, start before end, with whole seconds in Vietnam time");
    return new SavedFilterCriteria(
        c.name().trim(),
        c.latitude(),
        c.longitude(),
        c.radiusKm(),
        text(c.keyword()),
        text(c.categoryCode()) == null ? null : c.categoryCode().trim().toUpperCase(Locale.ROOT),
        c.maxPrice(),
        c.minDiscountPercent(),
        c.pickupFrom(),
        c.pickupTo());
  }

  private static String text(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
