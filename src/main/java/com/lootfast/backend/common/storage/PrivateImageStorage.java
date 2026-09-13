package com.lootfast.backend.common.storage;

import java.time.Duration;

public interface PrivateImageStorage {

    StoredPrivateImage uploadPrivate(String storageKey, byte[] content);

    String createDownloadUrl(
            String storageKey,
            String format,
            Duration validity
    );
}
