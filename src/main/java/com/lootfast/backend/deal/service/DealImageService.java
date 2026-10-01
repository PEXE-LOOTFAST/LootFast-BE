package com.lootfast.backend.deal.service;

import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.common.storage.ImageFileValidator;
import com.lootfast.backend.common.storage.ImageStorage;
import com.lootfast.backend.common.storage.ImageStorageException;
import com.lootfast.backend.common.storage.ImageUploadPreset;
import com.lootfast.backend.common.storage.ImageValidationException;
import com.lootfast.backend.common.storage.ImageValidationFailure;
import com.lootfast.backend.common.storage.StoredImage;
import com.lootfast.backend.deal.dto.MerchantDealResponse;
import com.lootfast.backend.deal.dto.ReorderDealImagesRequest;
import com.lootfast.backend.deal.entity.Deal;
import com.lootfast.backend.deal.entity.DealImage;
import com.lootfast.backend.deal.entity.DealImageStatus;
import com.lootfast.backend.deal.entity.DealStatus;
import com.lootfast.backend.deal.repository.DealImageRepository;
import com.lootfast.backend.deal.repository.DealRepository;
import com.lootfast.backend.merchant.repository.MerchantProfileRepository;
import com.lootfast.backend.merchant.service.MerchantImageUploadRateLimiter;
import com.lootfast.backend.user.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
public class DealImageService {

    private static final Logger log = LoggerFactory.getLogger(DealImageService.class);
    private static final int MAX_IMAGES = 5;
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final long MAX_PIXEL_COUNT = 25_000_000L;

    private final MerchantDealAccess access;
    private final MerchantProfileRepository merchantRepository;
    private final DealRepository dealRepository;
    private final DealImageRepository imageRepository;
    private final ImageStorage imageStorage;
    private final ImageFileValidator imageValidator;
    private final MerchantImageUploadRateLimiter uploadRateLimiter;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public DealImageService(
            MerchantDealAccess access,
            MerchantProfileRepository merchantRepository,
            DealRepository dealRepository,
            DealImageRepository imageRepository,
            ImageStorage imageStorage,
            ImageFileValidator imageValidator,
            MerchantImageUploadRateLimiter uploadRateLimiter,
            PlatformTransactionManager transactionManager,
            Clock clock
    ) {
        this.access = access;
        this.merchantRepository = merchantRepository;
        this.dealRepository = dealRepository;
        this.imageRepository = imageRepository;
        this.imageStorage = imageStorage;
        this.imageValidator = imageValidator;
        this.uploadRateLimiter = uploadRateLimiter;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setTimeout(10);
        this.clock = clock;
    }

    public MerchantDealResponse upload(String subject, UUID dealId, MultipartFile file) {
        User merchant = access.requireApprovedMerchant(subject);
        uploadRateLimiter.requireAllowed(merchant.getId());
        byte[] content = validateAndRead(file);
        Deal readableDeal = dealRepository.findOwned(dealId, merchant.getId())
                .orElseThrow(this::dealNotFound);
        if (readableDeal.getStatus() == DealStatus.REMOVED) {
            throw new ApiException(ErrorCode.DEAL_NOT_EDITABLE, "Removed deals cannot accept images");
        }

        String storageKey = "lootfast/deals/"
                + merchant.getPublicId()
                + "/"
                + dealId
                + "/"
                + UUID.randomUUID();
        StoredImage stored;
        try {
            stored = imageStorage.upload(storageKey, content, ImageUploadPreset.DEAL_IMAGE);
        } catch (ImageStorageException exception) {
            throw new ApiException(
                    ErrorCode.DEAL_IMAGE_UPLOAD_FAILED,
                    "Deal image could not be uploaded at this time"
            );
        }

        try {
            MerchantDealResponse response = transactions.execute(status ->
                    saveUploadedImage(merchant, dealId, stored)
            );
            if (response == null) {
                throw new IllegalStateException("Image transaction did not return a response");
            }
            return response;
        } catch (RuntimeException exception) {
            deleteStoredImageQuietly(stored.storageKey());
            throw exception;
        }
    }

    public MerchantDealResponse reorder(
            String subject,
            UUID dealId,
            ReorderDealImagesRequest request
    ) {
        User merchant = access.requireApprovedMerchant(subject);
        MerchantDealResponse response = transactions.execute(status -> {
            lockMerchant(merchant.getId());
            Deal deal = findOwnedForUpdate(dealId, merchant.getId());
            List<DealImage> images = activeImages(deal.getId());
            List<UUID> requested = request.imageIds();
            if (requested.size() != images.size()
                    || new HashSet<>(requested).size() != requested.size()
                    || !images.stream().map(DealImage::getPublicId).collect(java.util.stream.Collectors.toSet())
                    .equals(new HashSet<>(requested))) {
                throw new ApiException(
                        ErrorCode.VALIDATION_FAILED,
                        "Image order must contain every active image exactly once"
                );
            }
            var byId = images.stream().collect(java.util.stream.Collectors.toMap(
                    DealImage::getPublicId,
                    image -> image
            ));
            for (int index = 0; index < requested.size(); index++) {
                byId.get(requested.get(index)).reorder(index);
            }
            deal.updatePrimaryImage(byId.get(requested.getFirst()).getImageUrl());
            imageRepository.flush();
            dealRepository.flush();
            return MerchantDealResponse.from(deal, activeImages(deal.getId()));
        });
        if (response == null) {
            throw new IllegalStateException("Image reorder transaction did not return a response");
        }
        return response;
    }

    public MerchantDealResponse removeByMerchant(
            String subject,
            UUID dealId,
            UUID imageId
    ) {
        User merchant = access.requireApprovedMerchant(subject);
        RemovedImage result = transactions.execute(status -> {
            lockMerchant(merchant.getId());
            Deal deal = findOwnedForUpdate(dealId, merchant.getId());
            List<DealImage> images = activeImages(deal.getId());
            DealImage image = images.stream()
                    .filter(candidate -> candidate.getPublicId().equals(imageId))
                    .findFirst()
                    .orElseThrow(this::imageNotFound);
            if (images.size() == 1 && deal.getStatus() != DealStatus.DRAFT) {
                throw new ApiException(
                        ErrorCode.DEAL_IMAGE_REQUIRED,
                        "A published deal must keep at least one image"
                );
            }
            image.remove(merchant, "Removed by merchant", clock.instant());
            List<DealImage> remaining = images.stream()
                    .filter(candidate -> !candidate.getPublicId().equals(imageId))
                    .toList();
            reorderSequentially(remaining);
            deal.updatePrimaryImage(remaining.isEmpty() ? null : remaining.getFirst().getImageUrl());
            imageRepository.flush();
            dealRepository.flush();
            return new RemovedImage(
                    image.getStorageKey(),
                    MerchantDealResponse.from(deal, remaining)
            );
        });
        if (result == null) {
            throw new IllegalStateException("Image removal transaction did not return a response");
        }
        deleteStoredImageQuietly(result.storageKey());
        return result.response();
    }

    public MerchantDealResponse removeByAdmin(
            String subject,
            UUID imageId,
            String reason
    ) {
        User admin = access.requireAdmin(subject);
        RemovedImage result = transactions.execute(status -> {
            DealImage readable = imageRepository.findByPublicId(imageId)
                    .orElseThrow(this::imageNotFound);
            Deal deal = dealRepository.findReservationForUpdate(readable.getDeal().getPublicId())
                    .orElseThrow(this::dealNotFound);
            DealImage locked = imageRepository.findByPublicIdForUpdate(imageId)
                    .orElseThrow(this::imageNotFound);
            List<DealImage> images = activeImages(deal.getId());
            DealImage image = images.stream()
                    .filter(candidate -> candidate.getPublicId().equals(locked.getPublicId()))
                    .findFirst()
                    .orElseThrow(this::imageNotFound);
            image.remove(admin, reason.trim(), clock.instant());
            List<DealImage> remaining = images.stream()
                    .filter(candidate -> !candidate.getPublicId().equals(imageId))
                    .toList();
            reorderSequentially(remaining);
            deal.updatePrimaryImage(remaining.isEmpty() ? null : remaining.getFirst().getImageUrl());
            if (remaining.isEmpty()) {
                deal.hide(clock.instant());
            }
            imageRepository.flush();
            dealRepository.flush();
            return new RemovedImage(
                    image.getStorageKey(),
                    MerchantDealResponse.from(deal, remaining)
            );
        });
        if (result == null) {
            throw new IllegalStateException("Admin image removal transaction did not return a response");
        }
        deleteStoredImageQuietly(result.storageKey());
        return result.response();
    }

    private MerchantDealResponse saveUploadedImage(
            User merchant,
            UUID dealId,
            StoredImage stored
    ) {
        lockMerchant(merchant.getId());
        Deal deal = findOwnedForUpdate(dealId, merchant.getId());
        if (deal.getStatus() == DealStatus.REMOVED) {
            throw new ApiException(ErrorCode.DEAL_NOT_EDITABLE, "Removed deals cannot accept images");
        }
        List<DealImage> existing = activeImages(deal.getId());
        if (existing.size() >= MAX_IMAGES) {
            throw new ApiException(
                    ErrorCode.DEAL_IMAGE_LIMIT_REACHED,
                    "A deal can contain at most 5 images"
            );
        }
        DealImage image = imageRepository.save(new DealImage(
                deal,
                stored.storageKey(),
                stored.url(),
                existing.size()
        ));
        if (existing.isEmpty()) {
            deal.updatePrimaryImage(stored.url());
        }
        imageRepository.flush();
        dealRepository.flush();
        List<DealImage> images = new java.util.ArrayList<>(existing);
        images.add(image);
        return MerchantDealResponse.from(deal, images);
    }

    private byte[] validateAndRead(MultipartFile file) {
        try {
            return imageValidator.validateAndRead(file, MAX_FILE_SIZE, MAX_PIXEL_COUNT);
        } catch (ImageValidationException exception) {
            if (exception.getFailure() == ImageValidationFailure.FILE_TOO_LARGE) {
                throw new ApiException(
                        ErrorCode.DEAL_IMAGE_TOO_LARGE,
                        "Deal image file must not exceed 5 MB"
                );
            }
            throw new ApiException(
                    ErrorCode.INVALID_DEAL_IMAGE,
                    "Deal image must be a valid JPEG or PNG file"
            );
        }
    }

    private void deleteStoredImageQuietly(String storageKey) {
        try {
            imageStorage.delete(storageKey);
        } catch (ImageStorageException exception) {
            log.warn("Could not delete deal image storage key {}; metadata remains removed", storageKey);
        }
    }

    private void reorderSequentially(List<DealImage> images) {
        for (int index = 0; index < images.size(); index++) {
            images.get(index).reorder(index);
        }
    }

    private void lockMerchant(Long merchantUserId) {
        merchantRepository.findByUserIdForUpdate(merchantUserId)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.MERCHANT_PROFILE_NOT_FOUND,
                        "Merchant profile was not found"
                ));
    }

    private Deal findOwnedForUpdate(UUID dealId, Long merchantUserId) {
        return dealRepository.findOwnedForUpdate(dealId, merchantUserId)
                .orElseThrow(this::dealNotFound);
    }

    private List<DealImage> activeImages(Long dealId) {
        return imageRepository.findAllByDealIdAndStatusOrderByDisplayOrderAscIdAsc(
                dealId,
                DealImageStatus.ACTIVE
        );
    }

    private ApiException dealNotFound() {
        return new ApiException(ErrorCode.DEAL_NOT_FOUND, "Deal was not found");
    }

    private ApiException imageNotFound() {
        return new ApiException(ErrorCode.DEAL_IMAGE_NOT_FOUND, "Deal image was not found");
    }

    private record RemovedImage(String storageKey, MerchantDealResponse response) {
    }
}
