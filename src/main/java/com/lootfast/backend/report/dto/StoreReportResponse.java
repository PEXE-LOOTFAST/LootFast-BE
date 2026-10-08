package com.lootfast.backend.report.dto;

import com.lootfast.backend.report.entity.ReportStatus;
import com.lootfast.backend.report.entity.ReportType;
import com.lootfast.backend.report.entity.StoreReport;
import java.time.Instant;
import java.util.UUID;

public record StoreReportResponse(
    UUID reportId,
    UUID orderId,
    UUID storeId,
    ReportType reportType,
    String description,
    String evidenceImageUrl,
    ReportStatus status,
    Instant createdAt) {
  public static StoreReportResponse from(StoreReport r) {
    return new StoreReportResponse(
        r.getPublicId(),
        r.getOrder().getPublicId(),
        r.getMerchant().getUser().getPublicId(),
        r.getReportType(),
        r.getDescription(),
        r.getEvidenceImageUrl(),
        r.getStatus(),
        r.getCreatedAt());
  }
}
