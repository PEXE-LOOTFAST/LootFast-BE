package com.lootfast.backend.common.storage;

import java.time.Duration;

public interface PrivateImageStorage {

    StoredPrivateImage uploadPrivate(String storageKey, byte[] content);

    void deletePrivate(String storageKey);

    String createDownloadUrl(
            String storageKey,
            String format,
            Duration validity
    );
}
