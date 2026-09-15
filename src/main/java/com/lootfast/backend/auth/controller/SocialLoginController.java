package com.lootfast.backend.auth.controller;

import com.lootfast.backend.auth.dto.SigninResponse;
import com.lootfast.backend.auth.dto.SocialLoginRequest;
import com.lootfast.backend.auth.dto.SocialNonceResponse;
import com.lootfast.backend.auth.social.SocialLoginService;
import com.lootfast.backend.auth.social.SocialNonceService;
import com.lootfast.backend.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Authentication")
@RestController
@RequestMapping("/api/v1/auth/social")
public class SocialLoginController {

    private final SocialNonceService nonceService;
    private final SocialLoginService socialLoginService;

    public SocialLoginController(
            SocialNonceService nonceService,
            SocialLoginService socialLoginService
    ) {
        this.nonceService = nonceService;
        this.socialLoginService = socialLoginService;
    }

    @Operation(
            summary = "Tạo nonce đăng nhập mạng xã hội",
            description = "Nonce dùng một lần, hết hạn sau 5 phút. Client phải đưa nonce vào yêu cầu Google/Apple trước khi gửi ID token về backend."
    )
    @PostMapping("/nonce")
    public ResponseEntity<ApiResponse<SocialNonceResponse>> issueNonce() {
        SocialNonceService.IssuedNonce issued = nonceService.issue();
        return ResponseEntity.ok(ApiResponse.success(
                new SocialNonceResponse(issued.nonce(), issued.expiresIn())
        ));
    }

    @Operation(
            summary = "Đăng nhập nhanh bằng Google hoặc Apple",
            description = "Backend xác minh chữ ký, issuer, audience, hạn token, email đã xác minh và nonce. Tài khoản mới luôn có role BUYER và phải gửi phiên bản điều khoản/chính sách đã đồng ý."
    )
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<SigninResponse>> login(
            @Valid @RequestBody SocialLoginRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(socialLoginService.login(request)));
    }
}
