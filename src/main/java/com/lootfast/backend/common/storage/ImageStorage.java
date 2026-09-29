package com.lootfast.backend.common.storage;

public interface ImageStorage {

    StoredImage upload(
            String storageKey,
            byte[] content,
            ImageUploadPreset preset
    );

    void delete(String storageKey);
}
