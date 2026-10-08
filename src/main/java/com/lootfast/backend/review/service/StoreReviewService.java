package com.lootfast.backend.review.service;

import com.lootfast.backend.common.exception.*;
import com.lootfast.backend.common.response.PagedResponse;
import com.lootfast.backend.config.DatabaseLockTimeout;
import com.lootfast.backend.merchant.repository.MerchantProfileRepository;
import com.lootfast.backend.order.entity.OrderStatus;
import com.lootfast.backend.order.repository.OrderRepository;
import com.lootfast.backend.review.dto.ReviewRequests.*;
import com.lootfast.backend.review.dto.ReviewResponses.*;
import com.lootfast.backend.review.entity.*;
import com.lootfast.backend.review.repository.*;
import com.lootfast.backend.user.entity.*;
import com.lootfast.backend.user.repository.UserRepository;
import jakarta.validation.Valid;
import java.time.Clock;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class StoreReviewService {
  private final StoreReviewRepository reviews;
  private final ReviewModerationRepository moderation;
  private final OrderRepository orders;
  private final MerchantProfileRepository merchants;
  private final UserRepository users;
  private final DatabaseLockTimeout locks;
  private final Clock clock;
  private final StoreRatingService ratings;

  public StoreReviewService(StoreReviewRepository reviews, ReviewModerationRepository moderation,
      OrderRepository orders, MerchantProfileRepository merchants, UserRepository users,
      DatabaseLockTimeout locks, Clock clock, StoreRatingService ratings) {
    this.reviews = reviews; this.moderation = moderation; this.orders = orders;
    this.merchants = merchants; this.users = users; this.locks = locks; this.clock = clock; this.ratings = ratings;
  }

  @Transactional(timeout = 5)
  public ManagedReview create(String subject, UUID orderId, @Valid Create request) {
    var buyer = actor(subject, UserRole.BUYER);
    locks.apply();
    var order = orders.findForUpdate(orderId).orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND));
    if (!order.getBuyer().getId().equals(buyer.getId())) throw new ApiException(ErrorCode.ORDER_NOT_FOUND);
    if (order.getStatus() != OrderStatus.COMPLETED)
      throw new ApiException(ErrorCode.REVIEW_NOT_ALLOWED, "Only completed orders can be reviewed");
    if (reviews.existsByOrderId(order.getId())) throw new ApiException(ErrorCode.REVIEW_ALREADY_EXISTS);
    return ManagedReview.from(reviews.saveAndFlush(new StoreReview(order, request.rating(),
        normalize(request.comment()), now())));
  }

  @Transactional(readOnly = true)
  public ManagedReview mine(String subject, UUID orderId) {
    var buyer = actor(subject, UserRole.BUYER);
    var review = reviews.findByOrderPublicId(orderId).orElseThrow(() -> new ApiException(ErrorCode.REVIEW_NOT_FOUND));
    if (!review.getBuyer().getId().equals(buyer.getId())) throw new ApiException(ErrorCode.REVIEW_NOT_FOUND);
    return ManagedReview.from(review);
  }

  @Transactional(readOnly = true)
  public PagedResponse<PublicReview> publicList(UUID storeId, int page, int size) {
    var store = merchants.findPublicByUserPublicId(storeId)
        .orElseThrow(() -> new ApiException(ErrorCode.MERCHANT_PROFILE_NOT_FOUND));
    return PagedResponse.from(reviews.findByMerchantIdAndHiddenFalse(store.getId(), page(page, size)).map(PublicReview::from));
  }

  @Transactional(readOnly = true)
  public Summary summary(Long merchantId) {
    return ratings.get(merchantId);
  }

  @Transactional(readOnly = true)
  public PagedResponse<ManagedReview> merchantList(String subject, int page, int size) {
    var actor = actor(subject, UserRole.MERCHANT);
    return PagedResponse.from(reviews.findByMerchantUserId(actor.getId(), page(page, size)).map(ManagedReview::from));
  }

  @Transactional(timeout = 5)
  public ManagedReview reply(String subject, UUID id, @Valid Reply request) {
    var merchant = actor(subject, UserRole.MERCHANT);
    var review = locked(id);
    if (!review.getMerchant().getUser().getId().equals(merchant.getId())) throw new ApiException(ErrorCode.REVIEW_NOT_FOUND);
    if (review.isHidden()) throw new ApiException(ErrorCode.REVIEW_REPLY_CONFLICT, "Hidden reviews cannot receive replies");
    String text = request.reply().strip();
    if (review.getReply() != null) {
      if (!review.getReply().equals(text)) throw new ApiException(ErrorCode.REVIEW_REPLY_CONFLICT, "A review can receive one reply");
    } else review.reply(text, now());
    return ManagedReview.from(review);
  }

  @Transactional(readOnly = true)
  public PagedResponse<ManagedReview> adminList(String subject, int page, int size) {
    actor(subject, UserRole.ADMIN);
    return PagedResponse.from(reviews.findAll(page(page, size)).map(ManagedReview::from));
  }

  @Transactional(timeout = 5)
  public ManagedReview visibility(String subject, UUID id, @Valid Visibility request) {
    var admin = actor(subject, UserRole.ADMIN);
    var review = locked(id);
    if (review.isHidden() != request.hidden()) {
      review.setHidden(request.hidden());
      moderation.save(new ReviewModerationEvent(review, admin, request.hidden(), request.reason().strip(), now()));
    }
    return ManagedReview.from(review);
  }

  @Transactional(readOnly = true)
  public PagedResponse<Moderation> history(String subject, UUID id, int page, int size) {
    actor(subject, UserRole.ADMIN);
    return PagedResponse.from(moderation.findByReviewPublicId(id, page(page, size)).map(Moderation::from));
  }

  // PostgreSQL timestamps have microsecond precision; keep replay responses stable.
  private java.time.Instant now() { return clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS); }

  private StoreReview locked(UUID id) {
    locks.apply();
    return reviews.lock(id).orElseThrow(() -> new ApiException(ErrorCode.REVIEW_NOT_FOUND));
  }
  private User actor(String subject, UserRole role) {
    Long id;
    try { id = Long.valueOf(subject); }
    catch (NumberFormatException | NullPointerException e) { throw new ApiException(ErrorCode.USER_NOT_FOUND); }
    var user = users.findById(id).orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    if (user.isLocked()) throw new ApiException(ErrorCode.ACCOUNT_LOCKED);
    if (user.getStatus() != UserStatus.VERIFIED) throw new ApiException(ErrorCode.ACCOUNT_NOT_VERIFIED);
    if (!user.hasRole(role)) throw new ApiException(ErrorCode.ACCESS_DENIED);
    return user;
  }
  private static String normalize(String text) { return text == null || text.isBlank() ? null : text.strip(); }
  private static Pageable page(int page, int size) {
    if (page < 0 || size < 1 || size > 50) throw new ApiException(ErrorCode.VALIDATION_FAILED);
    return PageRequest.of(page, size, Sort.by("createdAt").descending().and(Sort.by("id").descending()));
  }
}
