package com.lootfast.backend.user.controller;

import com.lootfast.backend.user.dto.CurrentUserResponse;
import com.lootfast.backend.user.service.CurrentUserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class CurrentUserController {

    private final CurrentUserService currentUserService;

    public CurrentUserController(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CurrentUserResponse> getCurrentUser(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(currentUserService.getCurrentUser(jwt.getSubject()));
    }
}
