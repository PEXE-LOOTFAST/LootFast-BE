package com.lootfast.backend.merchant.controller;

import com.lootfast.backend.common.response.ApiResponse;
import com.lootfast.backend.merchant.dto.MerchantImageResponse;
import com.lootfast.backend.merchant.service.MerchantImageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Merchant", description = "Quản lý hồ sơ cửa hàng của người bán")
@RestController
@RequestMapping("/api/v1/merchants")
public class MerchantImageController {

    private final MerchantImageService merchantImageService;

    public MerchantImageController(MerchantImageService merchantImageService) {
        this.merchantImageService = merchantImageService;
    }

    @Operation(
            summary = "Cập nhật logo cửa hàng",
            description = "Upload logo JPEG hoặc PNG, tối đa 5 MB.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Upload thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "Ảnh không hợp lệ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "Không phải merchant"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "Chưa có merchant profile"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "413", description = "File vượt quá 5 MB"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "429", description = "Vượt quá 10 lần upload mỗi giờ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "502", description = "Dịch vụ lưu ảnh không khả dụng"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "503", description = "Dịch vụ giới hạn upload không khả dụng")
    })
    @PutMapping(value = "/me/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('MERCHANT')")
    public ResponseEntity<ApiResponse<MerchantImageResponse>> uploadLogo(
            @AuthenticationPrincipal Jwt jwt,
            @RequestPart("file") MultipartFile file
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                merchantImageService.uploadLogo(jwt.getSubject(), file)
        ));
    }

    @Operation(
            summary = "Cập nhật ảnh bìa cửa hàng",
            description = "Upload ảnh bìa JPEG hoặc PNG, tối đa 5 MB.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Upload thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "Ảnh không hợp lệ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "Không phải merchant"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "Chưa có merchant profile"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "413", description = "File vượt quá 5 MB"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "429", description = "Vượt quá 10 lần upload mỗi giờ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "502", description = "Dịch vụ lưu ảnh không khả dụng"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "503", description = "Dịch vụ giới hạn upload không khả dụng")
    })
    @PutMapping(value = "/me/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('MERCHANT')")
    public ResponseEntity<ApiResponse<MerchantImageResponse>> uploadCover(
            @AuthenticationPrincipal Jwt jwt,
            @RequestPart("file") MultipartFile file
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                merchantImageService.uploadCover(jwt.getSubject(), file)
        ));
    }
}
