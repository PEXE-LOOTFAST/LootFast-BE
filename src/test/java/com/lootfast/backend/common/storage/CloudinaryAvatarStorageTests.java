package com.lootfast.backend.common.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.lootfast.backend.config.CloudinaryProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CloudinaryAvatarStorageTests {

    @Mock private Cloudinary cloudinary;
    @Mock private Uploader uploader;

    @Test
    void uploadUsesDeterministicKeyAndNormalizesImage() throws IOException {
        CloudinaryProperties properties = new CloudinaryProperties(
                "demo",
                "api-key",
                "api-secret"
        );
        CloudinaryAvatarStorage storage = new CloudinaryAvatarStorage(
                cloudinary,
                properties
        );
        UUID publicId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        byte[] content = new byte[]{1, 2, 3};
        String storageKey = "lootfast/user-avatars/" + publicId;
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(), any())).thenReturn(Map.of(
                "public_id", storageKey,
                "secure_url", "https://res.cloudinary.com/demo/avatar.png"
        ));

        StoredAvatar result = storage.upload(publicId, content);

        assertEquals(storageKey, result.storageKey());
        assertEquals("https://res.cloudinary.com/demo/avatar.png", result.url());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> optionsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(uploader).upload(same(content), optionsCaptor.capture());
        Map<String, Object> options = optionsCaptor.getValue();
        assertEquals(storageKey, options.get("public_id"));
        assertEquals("image", options.get("resource_type"));
        assertEquals("c_limit,h_1024,w_1024,q_auto", options.get("transformation"));
        assertEquals(true, options.get("overwrite"));
        assertEquals(true, options.get("invalidate"));
    }

    @Test
    void uploadFailsBeforeNetworkCallWhenCloudinaryIsNotConfigured() {
        CloudinaryAvatarStorage storage = new CloudinaryAvatarStorage(
                cloudinary,
                new CloudinaryProperties("", "", "")
        );

        assertThrows(
                AvatarStorageException.class,
                () -> storage.upload(UUID.randomUUID(), new byte[]{1})
        );
        verifyNoInteractions(cloudinary, uploader);
    }
}
