package com.lootfast.backend.support.service;

import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.common.response.PagedResponse;
import com.lootfast.backend.common.storage.ImageFileValidator;
import com.lootfast.backend.common.storage.ImageStorage;
import com.lootfast.backend.common.storage.ImageStorageException;
import com.lootfast.backend.common.storage.ImageUploadPreset;
import com.lootfast.backend.common.storage.ImageValidationException;
import com.lootfast.backend.common.storage.ImageValidationFailure;
import com.lootfast.backend.common.storage.StoredImage;
import com.lootfast.backend.merchant.entity.MerchantProfile;
import com.lootfast.backend.merchant.repository.MerchantProfileRepository;
import com.lootfast.backend.order.entity.CustomerOrder;
import com.lootfast.backend.order.repository.OrderRepository;
import com.lootfast.backend.support.dto.TicketResponse;
import com.lootfast.backend.support.entity.SupportTicket;
import com.lootfast.backend.support.entity.TicketCategory;
import com.lootfast.backend.support.entity.TicketEvent;
import com.lootfast.backend.support.entity.TicketEventAction;
import com.lootfast.backend.support.entity.TicketOrigin;
import com.lootfast.backend.support.repository.SupportTicketRepository;
import com.lootfast.backend.support.repository.TicketEventRepository;
import com.lootfast.backend.user.entity.User;
import com.lootfast.backend.user.entity.UserRole;
import com.lootfast.backend.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SupportTicketService {
  private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
  private static final long MAX_PIXEL_COUNT = 25_000_000L;

  private final UserRepository users;
  private final OrderRepository orders;
  private final MerchantProfileRepository merchants;
  private final SupportTicketRepository tickets;
  private final TicketEventRepository events;
  private final ImageStorage imageStorage;
  private final ImageFileValidator imageFileValidator;
  private final Clock clock;

  public SupportTicketService(
      UserRepository users,
      OrderRepository orders,
      MerchantProfileRepository merchants,
      SupportTicketRepository tickets,
      TicketEventRepository events,
      ImageStorage imageStorage,
      ImageFileValidator imageFileValidator,
      Clock clock) {
    this.users = users;
    this.orders = orders;
    this.merchants = merchants;
    this.tickets = tickets;
    this.events = events;
    this.imageStorage = imageStorage;
    this.imageFileValidator = imageFileValidator;
    this.clock = clock;
  }

  @Transactional
  public TicketResponse create(
      String subject,
      TicketCategory category,
      String title,
      String description,
      UUID orderId,
      UUID storeId,
      MultipartFile evidence) {
    Long reporterId = userId(subject);
    User reporter =
        users
            .findByIdWithRoles(reporterId)
            .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND, "User is unavailable"));

    CustomerOrder order = null;
    MerchantProfile merchant = null;
    if (orderId != null) {
      order =
          orders
              .findByPublicIdAndBuyerId(orderId, reporterId)
              .orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND, "Order was not found"));
      merchant = order.getMerchant();
    } else if (storeId != null) {
      merchant =
          merchants
              .findByUserPublicId(storeId)
              .orElseThrow(
                  () -> new ApiException(ErrorCode.MERCHANT_PROFILE_NOT_FOUND, "Store was not found"));
    }

    TicketOrigin origin =
        reporter.hasRole(UserRole.MERCHANT) ? TicketOrigin.MERCHANT : TicketOrigin.BUYER;
    Instant now = clock.instant();
    SupportTicket ticket =
        new SupportTicket(reporter, origin, category, order, merchant, title, description, now);
    if (evidence != null && !evidence.isEmpty()) {
      StoredImage stored = uploadEvidence(ticket.getPublicId(), evidence);
      ticket.attachEvidence(stored.url(), stored.storageKey(), now);
    }
    tickets.saveAndFlush(ticket);
    events.save(new TicketEvent(ticket, reporter, TicketEventAction.CREATED, null, now));
    return TicketResponse.from(ticket);
  }

  @Transactional(readOnly = true)
  public PagedResponse<TicketResponse> listMine(String subject, int page, int size) {
    var pageable =
        PageRequest.of(page, size, Sort.by("createdAt").descending().and(Sort.by("id").descending()));
    return PagedResponse.from(
        tickets.findByReporterId(userId(subject), pageable).map(TicketResponse::from));
  }

  @Transactional(readOnly = true)
  public TicketResponse get(String subject, UUID ticketId) {
    return TicketResponse.from(
        tickets
            .findByPublicIdAndReporterId(ticketId, userId(subject))
            .orElseThrow(() -> new ApiException(ErrorCode.TICKET_NOT_FOUND, "Ticket was not found")));
  }

  private StoredImage uploadEvidence(UUID ticketPublicId, MultipartFile evidence) {
    byte[] content;
    try {
      content = imageFileValidator.validateAndRead(evidence, MAX_FILE_SIZE, MAX_PIXEL_COUNT);
    } catch (ImageValidationException e) {
      if (e.getFailure() == ImageValidationFailure.FILE_TOO_LARGE)
        throw new ApiException(
            ErrorCode.TICKET_EVIDENCE_TOO_LARGE, "Evidence image must not exceed 5 MB");
      throw new ApiException(
          ErrorCode.INVALID_TICKET_EVIDENCE, "Evidence image must be a valid JPEG or PNG");
    }
    String storageKey = "lootfast/support-tickets/" + ticketPublicId + "/evidence";
    try {
      return imageStorage.upload(storageKey, content, ImageUploadPreset.TICKET_EVIDENCE);
    } catch (ImageStorageException e) {
      throw new ApiException(
          ErrorCode.TICKET_EVIDENCE_UPLOAD_FAILED, "Evidence image could not be uploaded");
    }
  }

  private static Long userId(String subject) {
    try {
      return Long.valueOf(subject);
    } catch (NumberFormatException | NullPointerException e) {
      throw new ApiException(ErrorCode.USER_NOT_FOUND, "User is unavailable");
    }
  }
}
