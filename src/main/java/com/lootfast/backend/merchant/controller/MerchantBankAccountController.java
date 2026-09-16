package com.lootfast.backend.merchant.controller;

import com.lootfast.backend.common.response.ApiResponse;
import com.lootfast.backend.merchant.dto.MerchantBankAccountResponse;
import com.lootfast.backend.merchant.dto.UpdateBankAccountRequest;
import com.lootfast.backend.merchant.service.MerchantBankAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Merchant - Bank account", description = "Tài khoản nhận tiền của cửa hàng (FR-ME-03)")
@RestController
@RequestMapping("/api/v1/merchants/me/bank-account")
@PreAuthorize("hasRole('MERCHANT')")
public class MerchantBankAccountController {

    private final MerchantBankAccountService bankAccountService;

    public MerchantBankAccountController(MerchantBankAccountService bankAccountService) {
        this.bankAccountService = bankAccountService;
    }

    @Operation(
            summary = "Xem tài khoản nhận tiền hiện tại",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping
    public ResponseEntity<ApiResponse<MerchantBankAccountResponse>> getMyBankAccount(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                bankAccountService.getMyBankAccount(jwt.getSubject())
        ));
    }

    @Operation(
            summary = "Thiết lập hoặc cập nhật tài khoản nhận tiền",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PutMapping
    public ResponseEntity<ApiResponse<MerchantBankAccountResponse>> upsertMyBankAccount(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateBankAccountRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                bankAccountService.upsertMyBankAccount(jwt.getSubject(), request)
        ));
    }
}
