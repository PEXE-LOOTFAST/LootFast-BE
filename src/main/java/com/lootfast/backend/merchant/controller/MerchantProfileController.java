package com.lootfast.backend.merchant.controller;

import com.lootfast.backend.common.response.ApiResponse;
import com.lootfast.backend.merchant.dto.MerchantLocationResponse;
import com.lootfast.backend.merchant.dto.MerchantOpeningHoursResponse;
import com.lootfast.backend.merchant.dto.MerchantProfileResponse;
import com.lootfast.backend.merchant.dto.UpdateMerchantLocationRequest;
import com.lootfast.backend.merchant.dto.UpdateMerchantProfileRequest;
import com.lootfast.backend.merchant.dto.UpdateOpeningHoursRequest;
import com.lootfast.backend.merchant.service.MerchantProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Merchant", description = "Quản lý hồ sơ cửa hàng của người bán")
@RestController
@RequestMapping("/api/v1/merchants/me")
@PreAuthorize("hasRole('MERCHANT')")
@SecurityRequirement(name = "bearerAuth")
public class MerchantProfileController {

    private final MerchantProfileService merchantProfileService;

    public MerchantProfileController(MerchantProfileService merchantProfileService) {
        this.merchantProfileService = merchantProfileService;
    }

    @Operation(summary = "Xem thông tin cửa hàng hiện tại")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Lấy thông tin thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "Không phải merchant"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "Chưa có merchant profile")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<MerchantProfileResponse>> getProfile(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                merchantProfileService.getProfile(jwt.getSubject())
        ));
    }

    @Operation(summary = "Tạo hoặc cập nhật thông tin cửa hàng")
    @PutMapping
    public ResponseEntity<ApiResponse<MerchantProfileResponse>> updateProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateMerchantProfileRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                merchantProfileService.updateProfile(jwt.getSubject(), request)
        ));
    }

    @Operation(summary = "Xem địa chỉ và vị trí cửa hàng")
    @GetMapping("/location")
    public ResponseEntity<ApiResponse<MerchantLocationResponse>> getLocation(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                merchantProfileService.getLocation(jwt.getSubject())
        ));
    }

    @Operation(summary = "Cập nhật địa chỉ và vị trí cửa hàng")
    @PutMapping("/location")
    public ResponseEntity<ApiResponse<MerchantLocationResponse>> updateLocation(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateMerchantLocationRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                merchantProfileService.updateLocation(jwt.getSubject(), request)
        ));
    }

    @Operation(summary = "Xem giờ hoạt động của cửa hàng")
    @GetMapping("/opening-hours")
    public ResponseEntity<ApiResponse<MerchantOpeningHoursResponse>> getOpeningHours(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                merchantProfileService.getOpeningHours(jwt.getSubject())
        ));
    }

    @Operation(
            summary = "Thay thế giờ hoạt động của cửa hàng",
            description = "Hỗ trợ nhiều khung giờ trong ngày; các khung giờ không được chồng lấn."
    )
    @PutMapping("/opening-hours")
    public ResponseEntity<ApiResponse<MerchantOpeningHoursResponse>> updateOpeningHours(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateOpeningHoursRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                merchantProfileService.updateOpeningHours(jwt.getSubject(), request)
        ));
    }
}
