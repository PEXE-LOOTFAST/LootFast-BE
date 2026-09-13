package com.lootfast.backend.common.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.lootfast.backend.config.CloudinaryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
public class CloudinaryPrivateImageStorage implements PrivateImageStorage {

    private static final Logger log = LoggerFactory.getLogger(
            CloudinaryPrivateImageStorage.class
    );

    private final Cloudinary cloudinary;
    private final CloudinaryProperties properties;

    public CloudinaryPrivateImageStorage(
            Cloudinary cloudinary,
            CloudinaryProperties properties
    ) {
        this.cloudinary = cloudinary;
        this.properties = properties;
    }

    @Override
    public StoredPrivateImage uploadPrivate(String storageKey, byte[] content) {
        requireConfiguration();

        try {
            Map<?, ?> result = cloudinary.uploader().upload(content, ObjectUtils.asMap(
                    "public_id", storageKey,
                    "resource_type", "image",
                    "type", "authenticated",
                    "overwrite", false
            ));

            return new StoredPrivateImage(
                    requiredValue(result, "public_id"),
                    requiredValue(result, "format")
            );
        } catch (IOException | RuntimeException exception) {
            log.error("Cloudinary private image upload failed for key {}", storageKey, exception);
            throw new ImageStorageException(
                    "Private image storage provider rejected the upload",
                    exception
            );
        }
    }

    @Override
    public String createDownloadUrl(
            String storageKey,
            String format,
            Duration validity
    ) {
        requireConfiguration();

        try {
            Map<String, Object> options = new HashMap<>();
            options.put("resource_type", "image");
            options.put("type", "authenticated");
            options.put("expires_at", Instant.now().plus(validity).getEpochSecond());
            return cloudinary.privateDownload(storageKey, format, options);
        } catch (Exception exception) {
            log.error("Cloudinary private download URL generation failed for key {}", storageKey,
                    exception);
            throw new ImageStorageException(
                    "Private image download URL could not be generated",
                    exception
            );
        }
    }

    private void requireConfiguration() {
        if (!properties.isConfigured()) {
            throw new ImageStorageException("Cloudinary image storage is not configured");
        }
    }

    private String requiredValue(Map<?, ?> result, String key) {
        Object value = result.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new ImageStorageException(
                    "Private image storage provider returned an incomplete response"
            );
        }
        return value.toString();
    }
}
