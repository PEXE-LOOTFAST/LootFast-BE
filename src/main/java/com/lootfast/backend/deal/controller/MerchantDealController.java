package com.lootfast.backend.deal.controller;

import com.lootfast.backend.common.response.ApiResponse;
import com.lootfast.backend.common.response.PagedResponse;
import com.lootfast.backend.deal.dto.CreateMerchantDealRequest;
import com.lootfast.backend.deal.dto.MerchantDealResponse;
import com.lootfast.backend.deal.dto.DealTemplateResponse;
import com.lootfast.backend.deal.dto.RemoveDealRequest;
import com.lootfast.backend.deal.dto.ReorderDealImagesRequest;
import com.lootfast.backend.deal.dto.UpdateDealQuantityRequest;
import com.lootfast.backend.deal.service.DealImageService;
import com.lootfast.backend.deal.service.MerchantDealService;
import com.lootfast.backend.deal.service.DealTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@Tag(name = "Merchant deals", description = "Tạo, chuẩn bị và đăng deal của cửa hàng")
@Validated
@RestController
@RequestMapping("/api/v1/merchants/me/deals")
@PreAuthorize("hasRole('MERCHANT')")
@SecurityRequirement(name = "bearerAuth")
public class MerchantDealController {

    private final MerchantDealService dealService;
    private final DealImageService imageService;
    private final DealTemplateService templateService;

    public MerchantDealController(
            MerchantDealService dealService,
            DealImageService imageService,
            DealTemplateService templateService
    ) {
        this.dealService = dealService;
        this.imageService = imageService;
        this.templateService = templateService;
    }

    @Operation(summary = "Lưu thông tin deal thành mẫu tái sử dụng")
    @PostMapping("/{dealId}/template")
    public ResponseEntity<ApiResponse<DealTemplateResponse>> saveTemplate(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID dealId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                templateService.save(jwt.getSubject(), dealId)
        ));
    }

    @Operation(summary = "Tạm ẩn deal khỏi khách hàng mới")
    @PostMapping("/{dealId}/hide")
    public ResponseEntity<ApiResponse<MerchantDealResponse>> hide(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID dealId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                dealService.hide(jwt.getSubject(), dealId)
        ));
    }

    @Operation(summary = "Mở lại deal đang tạm ẩn")
    @PostMapping("/{dealId}/restore")
    public ResponseEntity<ApiResponse<MerchantDealResponse>> restore(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID dealId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                dealService.restore(jwt.getSubject(), dealId)
        ));
    }

    @Operation(summary = "Gỡ vĩnh viễn deal nhưng giữ nguyên các đơn đã đặt")
    @PostMapping("/{dealId}/remove")
    public ResponseEntity<ApiResponse<MerchantDealResponse>> remove(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID dealId,
            @Valid @RequestBody RemoveDealRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                dealService.remove(jwt.getSubject(), dealId, request)
        ));
    }

    @Operation(summary = "Tạo bản nháp deal")
    @PostMapping
    public ResponseEntity<ApiResponse<MerchantDealResponse>> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateMerchantDealRequest request
    ) {
        MerchantDealResponse response = dealService.create(jwt.getSubject(), request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{dealId}")
                .buildAndExpand(response.dealId())
                .toUri();
        return ResponseEntity.created(location).body(ApiResponse.success(response));
    }

    @Operation(summary = "Danh sách deal của cửa hàng hiện tại")
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<MerchantDealResponse>>> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                dealService.list(jwt.getSubject(), page, size)
        ));
    }

    @Operation(summary = "Xem deal thuộc cửa hàng hiện tại")
    @GetMapping("/{dealId}")
    public ResponseEntity<ApiResponse<MerchantDealResponse>> get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID dealId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                dealService.get(jwt.getSubject(), dealId)
        ));
    }

    @Operation(summary = "Sửa thông tin bản nháp deal")
    @PutMapping("/{dealId}")
    public ResponseEntity<ApiResponse<MerchantDealResponse>> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID dealId,
            @Valid @RequestBody CreateMerchantDealRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                dealService.update(jwt.getSubject(), dealId, request)
        ));
    }

    @Operation(summary = "Đăng deal sau khi kiểm tra ảnh, giá, lịch và tồn kho")
    @PostMapping("/{dealId}/publish")
    public ResponseEntity<ApiResponse<MerchantDealResponse>> publish(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID dealId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                dealService.publish(jwt.getSubject(), dealId)
        ));
    }

    @Operation(summary = "Cập nhật tổng số lượng deal an toàn")
    @PatchMapping("/{dealId}/quantity")
    public ResponseEntity<ApiResponse<MerchantDealResponse>> updateQuantity(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID dealId,
            @Valid @RequestBody UpdateDealQuantityRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                dealService.updateQuantity(jwt.getSubject(), dealId, request)
        ));
    }

    @Operation(summary = "Thêm ảnh thật cho deal")
    @PostMapping(value = "/{dealId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<MerchantDealResponse>> uploadImage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID dealId,
            @RequestPart("file") MultipartFile file
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                imageService.upload(jwt.getSubject(), dealId, file)
        ));
    }

    @Operation(summary = "Sắp xếp lại ảnh; ảnh đầu tiên là ảnh chính")
    @PutMapping("/{dealId}/images/order")
    public ResponseEntity<ApiResponse<MerchantDealResponse>> reorderImages(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID dealId,
            @Valid @RequestBody ReorderDealImagesRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                imageService.reorder(jwt.getSubject(), dealId, request)
        ));
    }

    @Operation(summary = "Xóa một ảnh khỏi deal")
    @DeleteMapping("/{dealId}/images/{imageId}")
    public ResponseEntity<ApiResponse<MerchantDealResponse>> removeImage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID dealId,
            @PathVariable UUID imageId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                imageService.removeByMerchant(jwt.getSubject(), dealId, imageId)
        ));
    }
}
