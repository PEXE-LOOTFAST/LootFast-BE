package com.lootfast.backend.buyer.savedfilter.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record UpdateSavedFilterRequest(
    @NotNull @Min(0) Long expectedRevision, @Valid @NotNull SavedFilterCriteria criteria) {}
