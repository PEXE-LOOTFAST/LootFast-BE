package com.lootfast.backend.deal.controller;

import com.lootfast.backend.common.response.ApiResponse;
import com.lootfast.backend.common.response.PagedResponse;
import com.lootfast.backend.deal.dto.DealFeedItemResponse;
import com.lootfast.backend.deal.service.DealFeedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Deals")
@Validated
@RestController
@RequestMapping("/api/v1/deals")
public class DealFeedController {

    private final DealFeedService dealFeedService;

    public DealFeedController(DealFeedService dealFeedService) {
        this.dealFeedService = dealFeedService;
    }

    @Operation(
            summary = "Feed deal đang hoạt động theo vị trí",
            description = "Sắp xếp theo khoảng cách tăng dần. Bán kính mặc định 1 km, tối đa 50 km; kết quả được phân trang."
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
            @RequestParam(defaultValue = "0") @Min(0)
            int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50)
            int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                dealFeedService.getFeed(
                        latitude,
                        longitude,
                        radiusKm,
                        page,
                        size
                )
        ));
    }
}
