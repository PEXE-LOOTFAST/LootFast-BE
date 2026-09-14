package com.lootfast.backend.common.storage;

public enum ImageValidationFailure {
    FILE_REQUIRED,
    FILE_TOO_LARGE,
    UNSUPPORTED_MEDIA_TYPE,
    UNREADABLE_FILE,
    INVALID_IMAGE,
    INVALID_DIMENSIONS,
    MEDIA_TYPE_MISMATCH
}
