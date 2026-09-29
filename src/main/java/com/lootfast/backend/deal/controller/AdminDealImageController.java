package com.lootfast.backend.deal.controller;

import com.lootfast.backend.common.response.ApiResponse;
import com.lootfast.backend.deal.dto.MerchantDealResponse;
import com.lootfast.backend.deal.dto.RemoveDealImageRequest;
import com.lootfast.backend.deal.service.DealImageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Admin - Deal images", description = "Gỡ ảnh deal vi phạm")
@RestController
@RequestMapping("/api/v1/admin/deal-images")
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
public class AdminDealImageController {

    private final DealImageService imageService;

    public AdminDealImageController(DealImageService imageService) {
        this.imageService = imageService;
    }

    @Operation(summary = "Gỡ ảnh deal vi phạm và lưu lý do")
    @PostMapping("/{imageId}/remove")
    public ResponseEntity<ApiResponse<MerchantDealResponse>> remove(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID imageId,
            @Valid @RequestBody RemoveDealImageRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                imageService.removeByAdmin(jwt.getSubject(), imageId, request.reason())
        ));
    }
}
