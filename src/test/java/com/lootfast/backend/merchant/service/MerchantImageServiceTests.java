package com.lootfast.backend.merchant.service;

import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.common.storage.ImageFileValidator;
import com.lootfast.backend.common.storage.ImageStorage;
import com.lootfast.backend.common.storage.ImageStorageException;
import com.lootfast.backend.common.storage.ImageUploadPreset;
import com.lootfast.backend.common.storage.StoredImage;
import com.lootfast.backend.merchant.dto.MerchantImageResponse;
import com.lootfast.backend.merchant.entity.MerchantImageType;
import com.lootfast.backend.merchant.repository.MerchantProfileRepository;
import com.lootfast.backend.user.entity.User;
import com.lootfast.backend.user.entity.UserRole;
import com.lootfast.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MerchantImageServiceTests {

    private static final UUID USER_PUBLIC_ID = UUID.fromString(
            "550e8400-e29b-41d4-a716-446655440000"
    );

    @Mock private UserRepository userRepository;
    @Mock private MerchantProfileRepository merchantProfileRepository;
    @Mock private ImageStorage imageStorage;
    @Mock private MerchantImageUploadRateLimiter uploadRateLimiter;

    private MerchantImageService merchantImageService;

    @BeforeEach
    void setUp() {
        merchantImageService = new MerchantImageService(
                userRepository,
                merchantProfileRepository,
                imageStorage,
                new ImageFileValidator(),
                uploadRateLimiter
        );
    }

    @Test
    void uploadLogoUsesMerchantSpecificKeyAndUpdatesOnlyLogo() throws IOException {
        byte[] image = pngImage();
        MultipartFile file = imageFile("logo.png", image);
        User user = merchantUser();
        String storageKey = "lootfast/merchant-profiles/" + USER_PUBLIC_ID + "/logo";
        String imageUrl = "https://res.cloudinary.com/demo/logo.png";
        when(userRepository.findByIdWithRoles(42L)).thenReturn(Optional.of(user));
        when(merchantProfileRepository.existsByUserId(42L)).thenReturn(true);
        when(imageStorage.upload(
                storageKey,
                image,
                ImageUploadPreset.MERCHANT_LOGO
        )).thenReturn(new StoredImage(storageKey, imageUrl));
        when(merchantProfileRepository.updateLogoByUserId(
                eq(42L), eq(storageKey), eq(imageUrl), any()
        )).thenReturn(1);

        MerchantImageResponse response = merchantImageService.uploadLogo("42", file);

        assertEquals(MerchantImageType.LOGO, response.imageType());
        assertEquals(imageUrl, response.url());
        verify(merchantProfileRepository).updateLogoByUserId(
                eq(42L), eq(storageKey), eq(imageUrl), any()
        );
        verify(merchantProfileRepository, never())
                .updateCoverByUserId(any(), any(), any(), any());
    }

    @Test
    void uploadCoverUsesDifferentKeyAndPreset() throws IOException {
        byte[] image = pngImage();
        MultipartFile file = imageFile("cover.png", image);
        User user = merchantUser();
        String storageKey = "lootfast/merchant-profiles/" + USER_PUBLIC_ID + "/cover";
        String imageUrl = "https://res.cloudinary.com/demo/cover.png";
        when(userRepository.findByIdWithRoles(42L)).thenReturn(Optional.of(user));
        when(merchantProfileRepository.existsByUserId(42L)).thenReturn(true);
        when(imageStorage.upload(
                storageKey,
                image,
                ImageUploadPreset.MERCHANT_COVER
        )).thenReturn(new StoredImage(storageKey, imageUrl));
        when(merchantProfileRepository.updateCoverByUserId(
                eq(42L), eq(storageKey), eq(imageUrl), any()
        )).thenReturn(1);

        MerchantImageResponse response = merchantImageService.uploadCover("42", file);

        assertEquals(MerchantImageType.COVER, response.imageType());
        assertEquals(imageUrl, response.url());
        verify(merchantProfileRepository).updateCoverByUserId(
                eq(42L), eq(storageKey), eq(imageUrl), any()
        );
        verify(merchantProfileRepository, never())
                .updateLogoByUserId(any(), any(), any(), any());
    }

    @Test
    void uploadRejectsBuyerEvenWhenServiceIsCalledDirectly() throws IOException {
        User user = org.mockito.Mockito.mock(User.class);
        when(user.hasRole(UserRole.MERCHANT)).thenReturn(false);
        when(userRepository.findByIdWithRoles(42L)).thenReturn(Optional.of(user));

        ApiException exception = assertThrows(
                ApiException.class,
                () -> merchantImageService.uploadLogo(
                        "42",
                        imageFile("logo.png", pngImage())
                )
        );

        assertEquals(ErrorCode.ACCESS_DENIED, exception.getErrorCode());
        verifyNoInteractions(merchantProfileRepository, imageStorage);
    }

    @Test
    void uploadRejectsMerchantWithoutProfileBeforeCloudinaryCall() throws IOException {
        User user = org.mockito.Mockito.mock(User.class);
        when(user.hasRole(UserRole.MERCHANT)).thenReturn(true);
        when(userRepository.findByIdWithRoles(42L)).thenReturn(Optional.of(user));
        when(merchantProfileRepository.existsByUserId(42L)).thenReturn(false);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> merchantImageService.uploadLogo(
                        "42",
                        imageFile("logo.png", pngImage())
                )
        );

        assertEquals(ErrorCode.MERCHANT_PROFILE_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(imageStorage);
    }

    @Test
    void uploadRejectsInvalidImageBeforeDatabaseLookup() {
        MultipartFile file = new MockMultipartFile(
                "file",
                "logo.png",
                MediaType.IMAGE_PNG_VALUE,
                "not-an-image".getBytes()
        );

        ApiException exception = assertThrows(
                ApiException.class,
                () -> merchantImageService.uploadLogo("42", file)
        );

        assertEquals(ErrorCode.INVALID_MERCHANT_IMAGE, exception.getErrorCode());
        verifyNoInteractions(userRepository, merchantProfileRepository, imageStorage);
    }

    @Test
    void uploadMapsStorageFailureToSafeApiError() throws IOException {
        byte[] image = pngImage();
        User user = merchantUser();
        when(userRepository.findByIdWithRoles(42L)).thenReturn(Optional.of(user));
        when(merchantProfileRepository.existsByUserId(42L)).thenReturn(true);
        when(imageStorage.upload(any(), any(), any()))
                .thenThrow(new ImageStorageException("provider unavailable"));

        ApiException exception = assertThrows(
                ApiException.class,
                () -> merchantImageService.uploadCover(
                        "42",
                        imageFile("cover.png", image)
                )
        );

        assertEquals(ErrorCode.MERCHANT_IMAGE_UPLOAD_FAILED, exception.getErrorCode());
        verify(merchantProfileRepository, never())
                .updateCoverByUserId(any(), any(), any(), any());
    }

    @Test
    void rateLimitStopsRepeatedRequestBeforeCloudinaryCall() throws IOException {
        byte[] image = pngImage();
        User user = merchantUser();
        when(userRepository.findByIdWithRoles(42L)).thenReturn(Optional.of(user));
        when(merchantProfileRepository.existsByUserId(42L)).thenReturn(true);
        when(imageStorage.upload(any(), any(), any())).thenReturn(
                new StoredImage("merchant-logo-key", "https://example.test/logo.png")
        );
        when(merchantProfileRepository.updateLogoByUserId(
                eq(42L), any(), any(), any()
        )).thenReturn(1);
        doNothing().doThrow(new ApiException(
                ErrorCode.MERCHANT_IMAGE_RATE_LIMITED,
                "rate limited"
        )).when(uploadRateLimiter).requireAllowed(42L);

        merchantImageService.uploadLogo("42", imageFile("logo.png", image));
        ApiException exception = assertThrows(
                ApiException.class,
                () -> merchantImageService.uploadLogo("42", imageFile("logo.png", image))
        );

        assertEquals(ErrorCode.MERCHANT_IMAGE_RATE_LIMITED, exception.getErrorCode());
        verify(imageStorage, times(1)).upload(any(), any(), any());
        verify(userRepository, times(1)).findByIdWithRoles(42L);
    }

    private User merchantUser() {
        User user = org.mockito.Mockito.mock(User.class);
        when(user.hasRole(UserRole.MERCHANT)).thenReturn(true);
        when(user.getPublicId()).thenReturn(USER_PUBLIC_ID);
        return user;
    }

    private MultipartFile imageFile(String filename, byte[] content) {
        return new MockMultipartFile(
                "file",
                filename,
                MediaType.IMAGE_PNG_VALUE,
                content
        );
    }

    private byte[] pngImage() throws IOException {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
