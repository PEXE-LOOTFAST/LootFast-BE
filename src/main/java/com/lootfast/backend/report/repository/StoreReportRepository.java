package com.lootfast.backend.report.repository;

import com.lootfast.backend.report.entity.ReportType;
import com.lootfast.backend.report.entity.StoreReport;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreReportRepository extends JpaRepository<StoreReport, Long> {

  boolean existsByOrderIdAndReporterIdAndReportType(
      Long orderId, Long reporterId, ReportType reportType);

  @EntityGraph(attributePaths = {"merchant", "merchant.user", "order"})
  Page<StoreReport> findByReporterId(Long reporterId, Pageable pageable);

  long countByMerchantIdAndReportType(Long merchantId, ReportType reportType);

  long countByMerchantIdAndReportTypeAndCreatedAtAfter(
      Long merchantId, ReportType reportType, Instant after);
}
