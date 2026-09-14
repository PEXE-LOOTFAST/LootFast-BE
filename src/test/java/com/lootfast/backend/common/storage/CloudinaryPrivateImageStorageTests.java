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
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CloudinaryPrivateImageStorageTests {

    @Mock private Cloudinary cloudinary;
    @Mock private Uploader uploader;

    @Test
    void uploadUsesAuthenticatedDeliveryAndDoesNotOverwrite() throws IOException {
        CloudinaryPrivateImageStorage storage = configuredStorage();
        byte[] content = new byte[]{1, 2, 3};
        String storageKey = "lootfast/merchant-applications/user/submission/license";
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(), any())).thenReturn(Map.of(
                "public_id", storageKey,
                "format", "png"
        ));

        StoredPrivateImage result = storage.uploadPrivate(storageKey, content);

        assertEquals(storageKey, result.storageKey());
        assertEquals("png", result.format());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> options = ArgumentCaptor.forClass(Map.class);
        verify(uploader).upload(same(content), options.capture());
        assertEquals("authenticated", options.getValue().get("type"));
        assertEquals(false, options.getValue().get("overwrite"));
    }

    @Test
    void downloadUrlIsSignedForAuthenticatedAssetAndExpires() throws Exception {
        CloudinaryPrivateImageStorage storage = configuredStorage();
        String storageKey = "lootfast/merchant-applications/user/submission/license";
        when(cloudinary.privateDownload(eq(storageKey), eq("png"), any()))
                .thenReturn("https://api.cloudinary.test/private-download");
        long before = Instant.now().plus(Duration.ofMinutes(4)).getEpochSecond();

        String url = storage.createDownloadUrl(storageKey, "png", Duration.ofMinutes(5));

        assertEquals("https://api.cloudinary.test/private-download", url);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> options = ArgumentCaptor.forClass(Map.class);
        verify(cloudinary).privateDownload(eq(storageKey), eq("png"), options.capture());
        assertEquals("authenticated", options.getValue().get("type"));
        assertEquals("image", options.getValue().get("resource_type"));
        assertTrue((Long) options.getValue().get("expires_at") > before);
    }

    @Test
    void deleteUsesAuthenticatedDeliveryType() throws IOException {
        CloudinaryPrivateImageStorage storage = configuredStorage();
        String storageKey = "lootfast/merchant-applications/user/submission/license";
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.destroy(eq(storageKey), any())).thenReturn(Map.of("result", "ok"));

        storage.deletePrivate(storageKey);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> options = ArgumentCaptor.forClass(Map.class);
        verify(uploader).destroy(eq(storageKey), options.capture());
        assertEquals("authenticated", options.getValue().get("type"));
        assertEquals("image", options.getValue().get("resource_type"));
        assertEquals(true, options.getValue().get("invalidate"));
    }

    private CloudinaryPrivateImageStorage configuredStorage() {
        return new CloudinaryPrivateImageStorage(
                cloudinary,
                new CloudinaryProperties("demo", "api-key", "api-secret")
        );
    }
}
