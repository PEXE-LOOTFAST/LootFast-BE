package com.lootfast.backend.user.service;

import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.common.storage.AvatarStorage;
import com.lootfast.backend.common.storage.AvatarStorageException;
import com.lootfast.backend.common.storage.StoredAvatar;
import com.lootfast.backend.user.dto.AvatarResponse;
import com.lootfast.backend.user.entity.User;
import com.lootfast.backend.user.entity.UserProfile;
import com.lootfast.backend.user.repository.UserProfileRepository;
import com.lootfast.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAvatarServiceTests {

    @Mock private UserRepository userRepository;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private AvatarStorage avatarStorage;

    private UserAvatarService userAvatarService;

    @BeforeEach
    void setUp() {
        userAvatarService = new UserAvatarService(
                userRepository,
                userProfileRepository,
                avatarStorage
        );
    }

    @Test
    void uploadAvatarStoresImageAndUpdatesProfile() throws IOException {
        UUID publicId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        byte[] image = pngImage();
        User user = mock(User.class);
        UserProfile profile = new UserProfile(user, "Kien");
        MultipartFile file = new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                image
        );

        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        when(user.getPublicId()).thenReturn(publicId);
        when(userProfileRepository.findByUserIdForUpdate(42L))
                .thenReturn(Optional.of(profile));
        when(avatarStorage.upload(publicId, image)).thenReturn(new StoredAvatar(
                "lootfast/user-avatars/" + publicId,
                "https://res.cloudinary.com/demo/avatar.png"
        ));

        AvatarResponse response = userAvatarService.uploadAvatar("42", file);

        assertEquals("https://res.cloudinary.com/demo/avatar.png", response.avatarUrl());
        assertEquals("https://res.cloudinary.com/demo/avatar.png", profile.getAvatarUrl());
        verify(avatarStorage).upload(publicId, image);
    }

    @Test
    void uploadAvatarRejectsUnsupportedMediaTypeBeforeDatabaseLookup() {
        MultipartFile file = new MockMultipartFile(
                "file",
                "avatar.gif",
                "image/gif",
                new byte[]{1, 2, 3}
        );

        ApiException exception = assertThrows(
                ApiException.class,
                () -> userAvatarService.uploadAvatar("42", file)
        );

        assertEquals(ErrorCode.INVALID_AVATAR, exception.getErrorCode());
        verifyNoInteractions(userRepository, userProfileRepository, avatarStorage);
    }

    @Test
    void uploadAvatarRejectsContentThatIsNotAnImage() {
        MultipartFile file = new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                "not-an-image".getBytes()
        );

        ApiException exception = assertThrows(
                ApiException.class,
                () -> userAvatarService.uploadAvatar("42", file)
        );

        assertEquals(ErrorCode.INVALID_AVATAR, exception.getErrorCode());
        verifyNoInteractions(userRepository, userProfileRepository, avatarStorage);
    }

    @Test
    void uploadAvatarRejectsFileLargerThanFiveMegabytes() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(5L * 1024 * 1024 + 1);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> userAvatarService.uploadAvatar("42", file)
        );

        assertEquals(ErrorCode.AVATAR_TOO_LARGE, exception.getErrorCode());
        verifyNoInteractions(userRepository, userProfileRepository, avatarStorage);
    }

    @Test
    void uploadAvatarMapsStorageFailureToSafeApiError() throws IOException {
        UUID publicId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        byte[] image = pngImage();
        User user = mock(User.class);
        UserProfile profile = new UserProfile(user, "Kien");
        MultipartFile file = new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                image
        );

        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        when(user.getPublicId()).thenReturn(publicId);
        when(userProfileRepository.findByUserIdForUpdate(42L))
                .thenReturn(Optional.of(profile));
        when(avatarStorage.upload(any(), any(byte[].class)))
                .thenThrow(new AvatarStorageException("provider unavailable"));

        ApiException exception = assertThrows(
                ApiException.class,
                () -> userAvatarService.uploadAvatar("42", file)
        );

        assertEquals(ErrorCode.AVATAR_UPLOAD_FAILED, exception.getErrorCode());
        assertNull(profile.getAvatarUrl());
    }

    private byte[] pngImage() throws IOException {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
