package com.lootfast.backend.favorite.controller;

import com.lootfast.backend.common.response.ApiResponse;
import com.lootfast.backend.common.response.PagedResponse;
import com.lootfast.backend.favorite.dto.FavoriteItemResponse;
import com.lootfast.backend.favorite.service.FavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Favorites")
@Validated
@RestController
@RequestMapping("/api/v1/users/me/favorites")
@SecurityRequirement(name = "bearerAuth")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @Operation(summary = "Xem danh sách deal và cửa hàng yêu thích")
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<FavoriteItemResponse>>> getFavorites(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                favoriteService.getFavorites(jwt.getSubject(), page, size)
        ));
    }

    @Operation(summary = "Lưu deal yêu thích")
    @PutMapping("/deals/{dealId}")
    public ResponseEntity<ApiResponse<FavoriteItemResponse>> saveDeal(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID dealId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                favoriteService.saveDeal(jwt.getSubject(), dealId)
        ));
    }

    @Operation(summary = "Bỏ lưu deal yêu thích")
    @DeleteMapping("/deals/{dealId}")
    public ResponseEntity<Void> removeDeal(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID dealId
    ) {
        favoriteService.removeDeal(jwt.getSubject(), dealId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Lưu cửa hàng yêu thích")
    @PutMapping("/stores/{merchantId}")
    public ResponseEntity<ApiResponse<FavoriteItemResponse>> saveStore(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID merchantId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                favoriteService.saveStore(jwt.getSubject(), merchantId)
        ));
    }

    @Operation(summary = "Bỏ lưu cửa hàng yêu thích")
    @DeleteMapping("/stores/{merchantId}")
    public ResponseEntity<Void> removeStore(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID merchantId
    ) {
        favoriteService.removeStore(jwt.getSubject(), merchantId);
        return ResponseEntity.noContent().build();
    }
}
