package com.lootfast.backend.user.admin.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record AccountActionRequest(@NotBlank @Size(max=500) String reason) {}
