package com.lootfast.backend.merchant.controller;

import com.lootfast.backend.common.response.ApiResponse;
import com.lootfast.backend.merchant.dto.PublicStoreProfileResponse;
import com.lootfast.backend.merchant.service.PublicStoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Stores")
@Validated
@RestController
@RequestMapping("/api/v1/stores")
public class PublicStoreController {

    private final PublicStoreService publicStoreService;

    public PublicStoreController(PublicStoreService publicStoreService) {
        this.publicStoreService = publicStoreService;
    }

    @Operation(
            summary = "Xem hồ sơ cửa hàng công khai",
            description = "Trả về thông tin, giờ mở cửa, đánh giá tổng hợp và các deal đang có."
    )
    @GetMapping("/{merchantId}")
    public ResponseEntity<ApiResponse<PublicStoreProfileResponse>> getStore(
            @PathVariable UUID merchantId,
            @RequestParam(required = false)
            @DecimalMin(value = "-90.0") @DecimalMax(value = "90.0")
            Double latitude,
            @RequestParam(required = false)
            @DecimalMin(value = "-180.0") @DecimalMax(value = "180.0")
            Double longitude,
            @RequestParam(defaultValue = "0") @Min(0)
            int dealPage,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50)
            int dealSize
    ) {
        return ResponseEntity.ok(ApiResponse.success(publicStoreService.getStore(
                merchantId,
                latitude,
                longitude,
                dealPage,
                dealSize
        )));
    }
}
