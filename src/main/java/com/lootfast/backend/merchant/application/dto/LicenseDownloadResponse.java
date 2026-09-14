package com.lootfast.backend.merchant.application.dto;

import java.time.Instant;

public record LicenseDownloadResponse(String url, Instant expiresAt) {
}
