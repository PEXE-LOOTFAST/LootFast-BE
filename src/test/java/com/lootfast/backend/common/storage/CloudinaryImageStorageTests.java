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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CloudinaryImageStorageTests {

    @Mock private Cloudinary cloudinary;
    @Mock private Uploader uploader;

    @Test
    void uploadUsesRequestedKeyAndPreset() throws IOException {
        CloudinaryImageStorage storage = configuredStorage();
        byte[] content = new byte[]{1, 2, 3};
        String storageKey = "lootfast/merchant-profiles/user-id/logo";
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(), any())).thenReturn(Map.of(
                "public_id", storageKey,
                "secure_url", "https://res.cloudinary.com/demo/logo.png"
        ));

        StoredImage result = storage.upload(
                storageKey,
                content,
                ImageUploadPreset.MERCHANT_LOGO
        );

        assertEquals(storageKey, result.storageKey());
        assertEquals("https://res.cloudinary.com/demo/logo.png", result.url());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> optionsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(uploader).upload(same(content), optionsCaptor.capture());
        Map<String, Object> options = optionsCaptor.getValue();
        assertEquals(storageKey, options.get("public_id"));
        assertEquals("image", options.get("resource_type"));
        assertEquals(
                ImageUploadPreset.MERCHANT_LOGO.transformation(),
                options.get("transformation")
        );
        assertEquals(true, options.get("overwrite"));
        assertEquals(true, options.get("invalidate"));
    }

    @Test
    void uploadFailsBeforeNetworkCallWhenCloudinaryIsNotConfigured() {
        CloudinaryImageStorage storage = new CloudinaryImageStorage(
                cloudinary,
                new CloudinaryProperties("", "", "")
        );

        assertThrows(
                ImageStorageException.class,
                () -> storage.upload(
                        "lootfast/test/image",
                        new byte[]{1},
                        ImageUploadPreset.USER_AVATAR
                )
        );
        verifyNoInteractions(cloudinary, uploader);
    }

    private CloudinaryImageStorage configuredStorage() {
        return new CloudinaryImageStorage(
                cloudinary,
                new CloudinaryProperties("demo", "api-key", "api-secret")
        );
    }
}
