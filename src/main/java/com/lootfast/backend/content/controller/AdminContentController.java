package com.lootfast.backend.content.controller;

import com.lootfast.backend.common.response.*;
import com.lootfast.backend.content.dto.ContentRequests.*;
import com.lootfast.backend.content.dto.ContentResponses.*;
import com.lootfast.backend.content.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@Validated
@Tag(name = "Admin content", description = "Danh mục, banner và tài liệu có phiên bản")
@SecurityRequirement(name = "bearerAuth")
public class AdminContentController {
  private final CategoryService categories;
  private final BannerService banners;
  private final BannerImageService images;
  private final LegalContentService legal;

  public AdminContentController(
      CategoryService categories,
      BannerService banners,
      BannerImageService images,
      LegalContentService legal) {
    this.categories = categories;
    this.banners = banners;
    this.images = images;
    this.legal = legal;
  }

  @GetMapping("/categories")
  @Operation(summary = "Danh sách tất cả danh mục, gồm danh mục đã tắt")
  public ApiResponse<PagedResponse<CategoryView>> categories(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
    return ApiResponse.success(categories.list(true, jwt.getSubject(), page, size));
  }

  @PostMapping("/categories")
  @Operation(summary = "Tạo danh mục")
  public ApiResponse<CategoryView> createCategory(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CategoryCreate r) {
    return ApiResponse.success(categories.create(jwt.getSubject(), r));
  }

  @PutMapping("/categories/{code}")
  @Operation(summary = "Sửa tên/thứ tự hoặc bật tắt danh mục; giữ nguyên mã")
  public ApiResponse<CategoryView> editCategory(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable String code,
      @Valid @RequestBody CategoryEdit r) {
    return ApiResponse.success(categories.edit(jwt.getSubject(), code, r));
  }

  @GetMapping("/banners")
  @Operation(summary = "Danh sách banner quản trị")
  public ApiResponse<PagedResponse<BannerView>> banners(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
    return ApiResponse.success(banners.list(true, jwt.getSubject(), page, size));
  }

  @PostMapping("/banners")
  @Operation(summary = "Tạo banner chưa bật, sau đó tải ảnh lên")
  public ApiResponse<BannerView> createBanner(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody BannerWrite r) {
    return ApiResponse.success(banners.write(jwt.getSubject(), null, r));
  }

  @PutMapping("/banners/{id}")
  @Operation(summary = "Sửa banner và lịch hiển thị")
  public ApiResponse<BannerView> editBanner(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody BannerWrite r) {
    return ApiResponse.success(banners.write(jwt.getSubject(), id, r));
  }

  @PostMapping(value = "/banners/{id}/image", consumes = "multipart/form-data")
  @Operation(summary = "Tải ảnh banner JPEG/PNG tối đa 5 MB")
  public ApiResponse<BannerView> upload(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @RequestParam @Min(0) long expectedRevision,
      @RequestPart("file") MultipartFile file) {
    return ApiResponse.success(images.upload(jwt.getSubject(), id, expectedRevision, file));
  }

  @GetMapping("/legal-documents")
  @Operation(summary = "Danh sách phiên bản tài liệu, gồm bản nháp")
  public ApiResponse<PagedResponse<LegalSummary>> legal(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
    return ApiResponse.success(legal.list(jwt.getSubject(), page, size));
  }

  @GetMapping("/legal-documents/{id}")
  @Operation(summary = "Xem nội dung phiên bản để quản trị")
  public ApiResponse<LegalView> getLegal(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return ApiResponse.success(legal.get(jwt.getSubject(), id));
  }

  @PostMapping("/legal-documents")
  @Operation(summary = "Tạo bản nháp điều khoản hoặc chính sách riêng tư; văn bản thuần")
  public ApiResponse<LegalView> createLegal(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody LegalCreate r) {
    return ApiResponse.success(legal.create(jwt.getSubject(), r));
  }

  @PutMapping("/legal-documents/{id}")
  @Operation(summary = "Sửa bản nháp; không sửa tài liệu đã phát hành")
  public ApiResponse<LegalView> editLegal(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody LegalEdit r) {
    return ApiResponse.success(legal.edit(jwt.getSubject(), id, r));
  }

  @PostMapping("/legal-documents/{id}/publish")
  @Operation(summary = "Phát hành phiên bản làm tài liệu hiện hành")
  public ApiResponse<LegalView> publish(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody Publish r) {
    return ApiResponse.success(legal.publish(jwt.getSubject(), id, r.expectedRevision()));
  }
}
