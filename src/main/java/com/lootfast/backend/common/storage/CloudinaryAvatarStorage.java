package com.lootfast.backend.common.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.lootfast.backend.config.CloudinaryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Component
public class CloudinaryAvatarStorage implements AvatarStorage {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryAvatarStorage.class);

    private final Cloudinary cloudinary;
    private final CloudinaryProperties properties;

    public CloudinaryAvatarStorage(
            Cloudinary cloudinary,
            CloudinaryProperties properties
    ) {
        this.cloudinary = cloudinary;
        this.properties = properties;
    }

    @Override
    public StoredAvatar upload(UUID userPublicId, byte[] content) {
        if (!properties.isConfigured()) {
            throw new AvatarStorageException("Cloudinary avatar storage is not configured");
        }

        String storageKey = "lootfast/user-avatars/" + userPublicId;

        try {
            Map<?, ?> result = cloudinary.uploader().upload(content, ObjectUtils.asMap(
                    "public_id", storageKey,
                    "resource_type", "image",
                    "transformation", "c_limit,h_1024,w_1024,q_auto",
                    "overwrite", true,
                    "invalidate", true
            ));

            String uploadedKey = requiredValue(result, "public_id");
            String secureUrl = requiredValue(result, "secure_url");
            return new StoredAvatar(uploadedKey, secureUrl);
        } catch (IOException | RuntimeException exception) {
            log.error("Cloudinary avatar upload failed for user {}", userPublicId, exception);
            throw new AvatarStorageException("Avatar storage provider rejected the upload", exception);
        }
    }

    private String requiredValue(Map<?, ?> result, String key) {
        Object value = result.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new AvatarStorageException(
                    "Avatar storage provider returned an incomplete response"
            );
        }
        return value.toString();
    }
}
