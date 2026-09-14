package com.lootfast.backend.common.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.lootfast.backend.config.CloudinaryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
public class CloudinaryImageStorage implements ImageStorage {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryImageStorage.class);

    private final Cloudinary cloudinary;
    private final CloudinaryProperties properties;

    public CloudinaryImageStorage(
            Cloudinary cloudinary,
            CloudinaryProperties properties
    ) {
        this.cloudinary = cloudinary;
        this.properties = properties;
    }

    @Override
    public StoredImage upload(
            String storageKey,
            byte[] content,
            ImageUploadPreset preset
    ) {
        if (!properties.isConfigured()) {
            throw new ImageStorageException("Cloudinary image storage is not configured");
        }

        try {
            Map<?, ?> result = cloudinary.uploader().upload(content, ObjectUtils.asMap(
                    "public_id", storageKey,
                    "resource_type", "image",
                    "transformation", preset.transformation(),
                    "overwrite", true,
                    "invalidate", true
            ));

            String uploadedKey = requiredValue(result, "public_id");
            String secureUrl = requiredValue(result, "secure_url");
            return new StoredImage(uploadedKey, secureUrl);
        } catch (IOException | RuntimeException exception) {
            log.error("Cloudinary image upload failed for key {}", storageKey, exception);
            throw new ImageStorageException("Image storage provider rejected the upload", exception);
        }
    }

    private String requiredValue(Map<?, ?> result, String key) {
        Object value = result.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new ImageStorageException(
                    "Image storage provider returned an incomplete response"
            );
        }
        return value.toString();
    }
}
