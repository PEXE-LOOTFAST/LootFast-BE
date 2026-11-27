package com.lootfast.backend.deal.controller;

import com.lootfast.backend.common.response.ApiResponse;
import com.lootfast.backend.common.response.PagedResponse;
import com.lootfast.backend.deal.dto.DealDetailResponse;
import com.lootfast.backend.deal.dto.DealDiscoveryQuery;
import com.lootfast.backend.deal.dto.DealFeedItemResponse;
import com.lootfast.backend.deal.service.DealDetailService;
import com.lootfast.backend.deal.service.DealFeedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@Tag(name = "Deals")
@Validated
@RestController
@RequestMapping("/api/v1/deals")
public class DealFeedController {

    private final DealFeedService dealFeedService;
    private final DealDetailService dealDetailService;

    public DealFeedController(
            DealFeedService dealFeedService,
            DealDetailService dealDetailService
    ) {
        this.dealFeedService = dealFeedService;
        this.dealDetailService = dealDetailService;
    }

    @Operation(
            summary = "Tìm và lọc deal đang hoạt động theo vị trí",
            description = "Tìm không dấu theo tên món/cửa hàng; lọc theo bán kính, danh mục, mức giảm và khoảng thời gian nhận. Kết quả được sắp xếp theo khoảng cách và phân trang. "
                    + "Khi bật DEMO_DEALS_ENABLED và khu vực không có deal thật phù hợp, trả tối đa 2 deal minh họa "
                    + "(demo=true, không có thật, không thể đặt)."
    )
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<DealFeedItemResponse>>> getFeed(
            @RequestParam
            @DecimalMin(value = "-90.0")
            @DecimalMax(value = "90.0")
            double latitude,
            @RequestParam
            @DecimalMin(value = "-180.0")
            @DecimalMax(value = "180.0")
            double longitude,
            @RequestParam(defaultValue = "1.0")
            @DecimalMin(value = "0.1")
            @DecimalMax(value = "50.0")
            double radiusKm,
            @RequestParam(required = false) @Size(max = 100)
            String keyword,
            @RequestParam(required = false) @Size(max = 100)
            String categoryCode,
            @RequestParam(required = false) @Min(0) @Max(100)
            Integer minDiscountPercent,
            @RequestParam(required = false)
            Instant pickupFrom,
            @RequestParam(required = false)
            Instant pickupTo,
            @RequestParam(defaultValue = "0") @Min(0)
            int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50)
            int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                dealFeedService.getFeed(new DealDiscoveryQuery(
                        latitude,
                        longitude,
                        radiusKm,
                        keyword,
                        categoryCode,
                        minDiscountPercent,
                        pickupFrom,
                        pickupTo,
                        page,
                        size
                ))
        ));
    }

    @Operation(
            summary = "Xem chi tiết deal theo thời gian thực",
            description = "remainingQuantity, inventoryVersion và updatedAt được đọc trực tiếp tại thời điểm request."
    )
    @GetMapping("/{dealId}")
    public ResponseEntity<ApiResponse<DealDetailResponse>> getDetail(
            @PathVariable UUID dealId,
            @RequestParam(required = false)
            @DecimalMin(value = "-90.0") @DecimalMax(value = "90.0")
            Double latitude,
            @RequestParam(required = false)
            @DecimalMin(value = "-180.0") @DecimalMax(value = "180.0")
            Double longitude
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                dealDetailService.getDetail(dealId, latitude, longitude)
        ));
    }
}
