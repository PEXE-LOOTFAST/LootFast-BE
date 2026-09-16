package com.lootfast.backend.auth.dto;

public record SocialNonceResponse(String nonce, long expiresIn) {
}
