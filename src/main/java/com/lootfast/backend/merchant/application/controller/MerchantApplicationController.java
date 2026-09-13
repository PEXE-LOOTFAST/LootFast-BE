package com.lootfast.backend.merchant.application.controller;

import com.lootfast.backend.common.response.ApiResponse;
import com.lootfast.backend.merchant.application.dto.MerchantApplicationResponse;
import com.lootfast.backend.merchant.application.service.MerchantApplicationSubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Merchant application", description = "Đăng ký trở thành người bán")
@RestController
@RequestMapping("/api/v1/merchant-applications")
public class MerchantApplicationController {

    private final MerchantApplicationSubmissionService submissionService;

    public MerchantApplicationController(
            MerchantApplicationSubmissionService submissionService
    ) {
        this.submissionService = submissionService;
    }

    @Operation(
            summary = "Gửi giấy phép để đăng ký người bán",
            description = "Chấp nhận ảnh JPEG/PNG tối đa 5 MB. Tài khoản vẫn là BUYER cho tới khi admin duyệt.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201", description = "Đã gửi và đang chờ admin duyệt"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "Đã có hồ sơ đang chờ hoặc đã được duyệt"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "413", description = "Ảnh vượt quá 5 MB"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "502", description = "Cloudinary tạm thời không khả dụng")
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<ApiResponse<MerchantApplicationResponse>> submit(
            @AuthenticationPrincipal Jwt jwt,
            @RequestPart("businessLicenseNumber") String businessLicenseNumber,
            @RequestPart("file") MultipartFile file
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                submissionService.submit(jwt.getSubject(), businessLicenseNumber, file)
        ));
    }

    @Operation(
            summary = "Xem trạng thái đăng ký người bán của tôi",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<MerchantApplicationResponse>> getCurrent(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                submissionService.getCurrent(jwt.getSubject())
        ));
    }
}
