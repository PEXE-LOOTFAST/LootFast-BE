package com.lootfast.backend.merchant.application.controller;

import com.lootfast.backend.common.response.ApiResponse;
import com.lootfast.backend.common.response.PagedResponse;
import com.lootfast.backend.merchant.application.dto.LicenseDownloadResponse;
import com.lootfast.backend.merchant.application.dto.MerchantApplicationResponse;
import com.lootfast.backend.merchant.application.dto.RejectMerchantApplicationRequest;
import com.lootfast.backend.merchant.application.entity.MerchantApplicationStatus;
import com.lootfast.backend.merchant.application.service.MerchantApplicationReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Admin - Merchant applications", description = "Duyệt đăng ký người bán")
@Validated
@RestController
@RequestMapping("/api/v1/admin/merchant-applications")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMerchantApplicationController {

    private final MerchantApplicationReviewService reviewService;

    public AdminMerchantApplicationController(MerchantApplicationReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @Operation(
            summary = "Danh sách hồ sơ đăng ký người bán",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<MerchantApplicationResponse>>> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "PENDING") MerchantApplicationStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                reviewService.list(jwt.getSubject(), status, page, size)
        ));
    }

    @Operation(
            summary = "Tạo link xem giấy phép trong 5 phút",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping("/{applicationId}/license-download-url")
    public ResponseEntity<ApiResponse<LicenseDownloadResponse>> createLicenseDownloadUrl(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID applicationId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                reviewService.createLicenseDownloadUrl(jwt.getSubject(), applicationId)
        ));
    }

    @Operation(
            summary = "Duyệt hồ sơ và thêm role MERCHANT",
            description = "Giữ nguyên role BUYER. User cần đăng nhập lại hoặc refresh token để token có role mới.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PostMapping("/{applicationId}/approve")
    public ResponseEntity<ApiResponse<MerchantApplicationResponse>> approve(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID applicationId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                reviewService.approve(jwt.getSubject(), applicationId)
        ));
    }

    @Operation(
            summary = "Từ chối hồ sơ",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PostMapping("/{applicationId}/reject")
    public ResponseEntity<ApiResponse<MerchantApplicationResponse>> reject(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID applicationId,
            @Valid @RequestBody RejectMerchantApplicationRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                reviewService.reject(jwt.getSubject(), applicationId, request.reason())
        ));
    }
}
