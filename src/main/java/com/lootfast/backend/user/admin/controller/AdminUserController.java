package com.lootfast.backend.user.admin.controller;
import com.lootfast.backend.common.response.*;
import com.lootfast.backend.user.admin.dto.*;
import com.lootfast.backend.user.admin.service.AdminUserService;
import com.lootfast.backend.user.entity.*;
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
@RestController
@Validated
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name="bearerAuth")
@Tag(name="Admin users",description="Tìm kiếm và khóa/mở khóa tài khoản")
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {
  private final AdminUserService service;
  public AdminUserController(AdminUserService service){this.service=service;}
  @GetMapping
  @Operation(summary="Tìm tài khoản theo tên/email/điện thoại và lọc vai trò, xác minh, khóa")
  public ApiResponse<PagedResponse<AdminUserResponse>> list(@AuthenticationPrincipal Jwt jwt,
      @RequestParam(required=false) @Size(max=100) String keyword,
      @RequestParam(required=false) UserRole role,
      @RequestParam(required=false) UserStatus verificationStatus,
      @RequestParam(required=false) Boolean locked,
      @RequestParam(defaultValue="0") @Min(0) int page,
      @RequestParam(defaultValue="20") @Min(1) @Max(50) int size) {
    return ApiResponse.success(service.list(jwt.getSubject(),keyword,role,verificationStatus,locked,page,size));
  }
  @GetMapping("/{userId}")
  @Operation(summary="Xem tài khoản theo public UUID")
  public ApiResponse<AdminUserResponse> get(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID userId){
    return ApiResponse.success(service.get(jwt.getSubject(),userId));
  }
  @GetMapping("/{userId}/actions")
  @Operation(summary="Xem lịch sử khóa/mở khóa tài khoản")
  public ApiResponse<PagedResponse<AccountAuditResponse>> history(@AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID userId,@RequestParam(defaultValue="0") @Min(0) int page,
      @RequestParam(defaultValue="20") @Min(1) @Max(50) int size){
    return ApiResponse.success(service.history(jwt.getSubject(),userId,page,size));
  }
  @PostMapping("/{userId}/lock")
  @Operation(summary="Khóa toàn bộ truy cập tài khoản và thu hồi phiên",
      description="Không tự hủy đơn cũ. Merchant không còn nhận đơn mới; dùng đình chỉ cửa hàng nếu vẫn cần xử lý đơn cũ.")
  public ApiResponse<AdminUserResponse> lock(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID userId,
      @Valid @RequestBody AccountActionRequest request){
    return ApiResponse.success(service.change(jwt.getSubject(),userId,request.reason(),true));
  }
  @PostMapping("/{userId}/unlock")
  @Operation(summary="Mở khóa; tài khoản phải đăng nhập lại, JWT cũ không phục hồi")
  public ApiResponse<AdminUserResponse> unlock(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID userId,
      @Valid @RequestBody AccountActionRequest request){
    return ApiResponse.success(service.change(jwt.getSubject(),userId,request.reason(),false));
  }
}
