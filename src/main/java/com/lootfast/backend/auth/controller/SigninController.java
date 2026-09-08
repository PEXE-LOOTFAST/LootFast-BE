package com.lootfast.backend.auth.controller;

import com.lootfast.backend.auth.dto.SigninRequest;
import com.lootfast.backend.auth.dto.SigninResponse;
import com.lootfast.backend.auth.service.SigninService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class SigninController {

    private final SigninService signinService;

    public SigninController(SigninService signinService) {
        this.signinService = signinService;
    }

    @PostMapping("/signin")
    public ResponseEntity<SigninResponse> signin(@Valid @RequestBody SigninRequest request) {
        return ResponseEntity.ok(signinService.signin(request));
    }
}
