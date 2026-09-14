package com.lootfast.backend.merchant.application;

import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.common.storage.ImageFileValidator;
import com.lootfast.backend.common.storage.ImageStorageException;
import com.lootfast.backend.common.storage.PrivateImageStorage;
import com.lootfast.backend.common.storage.StoredPrivateImage;
import com.lootfast.backend.merchant.application.service.MerchantApplicationSubmissionService;
import com.lootfast.backend.merchant.application.service.MerchantApplicationTransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MerchantApplicationSubmissionServiceTests {

    private static final UUID USER_PUBLIC_ID = UUID.fromString(
            "550e8400-e29b-41d4-a716-446655440000"
    );
    private static final UUID SUBMISSION_ID = UUID.fromString(
            "75c25375-4e41-4c86-a279-e1e49cbcb358"
    );
    private static final String NEW_STORAGE_KEY = "new-private-license";

    @Mock private ImageFileValidator imageFileValidator;
    @Mock private PrivateImageStorage privateImageStorage;
    @Mock private MerchantApplicationTransactionService transactionService;

    private MerchantApplicationSubmissionService service;

    @BeforeEach
    void setUp() {
        service = new MerchantApplicationSubmissionService(
                imageFileValidator,
                privateImageStorage,
                transactionService
        );
        when(imageFileValidator.validateAndRead(any(), any(Long.class), any(Long.class)))
                .thenReturn(new byte[]{1, 2, 3});
    }

    @Test
    void finalizationFailureDeletesUploadedAssetAndPreservesOriginalError() {
        ApiException original = new ApiException(
                ErrorCode.MERCHANT_APPLICATION_ALREADY_PENDING,
                "superseded"
        );
        prepareAttempt(null);
        prepareSuccessfulUpload();
        when(transactionService.finishSubmission(
                eq("42"), eq(SUBMISSION_ID), eq(NEW_STORAGE_KEY), eq("png")
        )).thenThrow(original);

        ApiException thrown = assertThrows(
                ApiException.class,
                () -> service.submit("42", "0312345678", imagePart())
        );

        assertSame(original, thrown);
        verify(privateImageStorage).deletePrivate(NEW_STORAGE_KEY);
        verify(transactionService).markUploadFailed("42", SUBMISSION_ID);
    }

    @Test
    void cleanupFailureDoesNotMaskFinalizationError() {
        ApiException original = new ApiException(
                ErrorCode.MERCHANT_APPLICATION_ALREADY_PENDING,
                "superseded"
        );
        prepareAttempt(null);
        prepareSuccessfulUpload();
        when(transactionService.finishSubmission(any(), any(), any(), any()))
                .thenThrow(original);
        doThrow(new ImageStorageException("delete unavailable"))
                .when(privateImageStorage).deletePrivate(NEW_STORAGE_KEY);

        ApiException thrown = assertThrows(
                ApiException.class,
                () -> service.submit("42", "0312345678", imagePart())
        );

        assertSame(original, thrown);
    }

    @Test
    void rejectedLicenseDeletionFailureStopsReplacementBeforeNewUpload() {
        prepareAttempt("previous-private-license");
        doThrow(new ImageStorageException("delete unavailable"))
                .when(privateImageStorage).deletePrivate("previous-private-license");

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.submit("42", "0312345678", imagePart())
        );

        assertEquals(ErrorCode.LICENSE_UPLOAD_FAILED, exception.getErrorCode());
        verify(privateImageStorage, never()).uploadPrivate(any(), any());
        verify(transactionService).markUploadFailed("42", SUBMISSION_ID);
    }

    private void prepareAttempt(String previousStorageKey) {
        when(transactionService.beginSubmission("42", "0312345678"))
                .thenReturn(new MerchantApplicationTransactionService.UploadAttempt(
                        USER_PUBLIC_ID,
                        SUBMISSION_ID,
                        previousStorageKey
                ));
    }

    private void prepareSuccessfulUpload() {
        when(privateImageStorage.uploadPrivate(any(), any()))
                .thenReturn(new StoredPrivateImage(NEW_STORAGE_KEY, "png"));
    }

    private MockMultipartFile imagePart() {
        return new MockMultipartFile(
                "file",
                "license.png",
                "image/png",
                new byte[]{1, 2, 3}
        );
    }
}
