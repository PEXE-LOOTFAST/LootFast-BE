package com.lootfast.backend.report.service;

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
import com.lootfast.backend.order.entity.CustomerOrder;
import com.lootfast.backend.order.repository.OrderRepository;
import com.lootfast.backend.report.dto.StoreReportResponse;
import com.lootfast.backend.report.entity.ReportType;
import com.lootfast.backend.report.entity.StoreReport;
import com.lootfast.backend.report.repository.StoreReportRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class StoreReportService {
  private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
  private static final long MAX_PIXEL_COUNT = 25_000_000L;
  // BR-MER-06: ≥3 báo "hết hàng" trong 30 ngày thì gắn cờ cho Admin rà soát.
  private static final int REVIEW_REPORT_THRESHOLD = 3;
  private static final long REVIEW_WINDOW_SECONDS = 30L * 24 * 3600;

  private final OrderRepository orders;
  private final StoreReportRepository reports;
  private final ImageStorage imageStorage;
  private final ImageFileValidator imageFileValidator;
  private final Clock clock;

  public StoreReportService(
      OrderRepository orders,
      StoreReportRepository reports,
      ImageStorage imageStorage,
      ImageFileValidator imageFileValidator,
      Clock clock) {
    this.orders = orders;
    this.reports = reports;
    this.imageStorage = imageStorage;
    this.imageFileValidator = imageFileValidator;
    this.clock = clock;
  }

  @Transactional
  public StoreReportResponse create(
      String subject,
      UUID orderPublicId,
      ReportType reportType,
      String description,
      MultipartFile evidence) {
    Long reporterId = userId(subject);
    // Ownership: khách chỉ báo cáo được đơn của chính mình; merchant lấy từ đơn.
    CustomerOrder order =
        orders
            .findByPublicIdAndBuyerId(orderPublicId, reporterId)
            .orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND, "Order was not found"));
    if (reports.existsByOrderIdAndReporterIdAndReportType(order.getId(), reporterId, reportType))
      throw new ApiException(
          ErrorCode.REPORT_ALREADY_EXISTS, "You already filed this report for this order");

    Instant now = clock.instant();
    StoreReport report =
        new StoreReport(
            order.getBuyer(), order.getMerchant(), order, reportType, description, now);
    if (evidence != null && !evidence.isEmpty()) {
      StoredImage stored = uploadEvidence(report.getPublicId(), evidence);
      report.attachEvidence(stored.url(), stored.storageKey(), now);
    }
    reports.saveAndFlush(report);
    flagForReviewIfTooManyOutOfStock(order.getMerchant(), reportType, now);
    return StoreReportResponse.from(report);
  }

  /** 068: khi vượt ngưỡng báo "hết hàng" trong 30 ngày, đánh dấu cửa hàng để Admin rà soát. */
  private void flagForReviewIfTooManyOutOfStock(
      com.lootfast.backend.merchant.entity.MerchantProfile merchant,
      ReportType reportType,
      Instant now) {
    if (reportType != ReportType.OUT_OF_STOCK || merchant.isFlaggedForReview()) return;
    long recent =
        reports.countByMerchantIdAndReportTypeAndCreatedAtAfter(
            merchant.getId(), ReportType.OUT_OF_STOCK, now.minusSeconds(REVIEW_WINDOW_SECONDS));
    if (recent >= REVIEW_REPORT_THRESHOLD) merchant.flagForReview(now);
  }

  @Transactional(readOnly = true)
  public PagedResponse<StoreReportResponse> listMine(String subject, int page, int size) {
    var pageable =
        PageRequest.of(page, size, Sort.by("createdAt").descending().and(Sort.by("id").descending()));
    return PagedResponse.from(
        reports.findByReporterId(userId(subject), pageable).map(StoreReportResponse::from));
  }

  private StoredImage uploadEvidence(UUID reportPublicId, MultipartFile evidence) {
    byte[] content;
    try {
      content = imageFileValidator.validateAndRead(evidence, MAX_FILE_SIZE, MAX_PIXEL_COUNT);
    } catch (ImageValidationException e) {
      if (e.getFailure() == ImageValidationFailure.FILE_TOO_LARGE)
        throw new ApiException(
            ErrorCode.REPORT_EVIDENCE_TOO_LARGE, "Evidence image must not exceed 5 MB");
      throw new ApiException(
          ErrorCode.INVALID_REPORT_EVIDENCE, "Evidence image must be a valid JPEG or PNG");
    }
    String storageKey = "lootfast/store-reports/" + reportPublicId + "/evidence";
    try {
      return imageStorage.upload(storageKey, content, ImageUploadPreset.STORE_REPORT_EVIDENCE);
    } catch (ImageStorageException e) {
      throw new ApiException(
          ErrorCode.REPORT_EVIDENCE_UPLOAD_FAILED, "Evidence image could not be uploaded");
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
