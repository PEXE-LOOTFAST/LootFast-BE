package com.lootfast.backend.content.controller;

import com.lootfast.backend.common.response.*;
import com.lootfast.backend.content.dto.ContentResponses.*;
import com.lootfast.backend.content.service.*;
import com.lootfast.backend.user.entity.ConsentDocument;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Validated
@Tag(name = "Public content")
public class PublicContentController {
  private final CategoryService categories;
  private final BannerService banners;
  private final LegalContentService legal;

  public PublicContentController(
      CategoryService categories, BannerService banners, LegalContentService legal) {
    this.categories = categories;
    this.banners = banners;
    this.legal = legal;
  }

  @GetMapping("/categories")
  @Operation(summary = "Danh mục đang bật để chọn khi đăng deal hoặc cài sở thích")
  public ApiResponse<PagedResponse<CategoryView>> categories(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
    return ApiResponse.success(categories.list(false, null, page, size));
  }

  @GetMapping("/banners")
  @Operation(summary = "Banner đang bật và trong thời gian hiển thị")
  public ApiResponse<PagedResponse<BannerView>> banners(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
    return ApiResponse.success(banners.list(false, null, page, size));
  }

  @GetMapping("/legal-documents/{document}/current")
  @Operation(summary = "Nội dung và phiên bản tài liệu hiện hành")
  public ApiResponse<LegalView> current(@PathVariable ConsentDocument document) {
    return ApiResponse.success(legal.current(document));
  }

  @GetMapping("/legal-documents/{document}/versions/{version}")
  @Operation(summary = "Đọc phiên bản đã phát hành trước đây")
  public ApiResponse<LegalView> version(
      @PathVariable ConsentDocument document, @PathVariable @Size(max = 50) String version) {
    return ApiResponse.success(legal.published(document, version));
  }
}
