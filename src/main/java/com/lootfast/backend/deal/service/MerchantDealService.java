package com.lootfast.backend.deal.service;

import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.common.response.PagedResponse;
import com.lootfast.backend.deal.dto.CreateMerchantDealRequest;
import com.lootfast.backend.deal.dto.MerchantDealResponse;
import com.lootfast.backend.deal.dto.UpdateDealQuantityRequest;
import com.lootfast.backend.deal.entity.Deal;
import com.lootfast.backend.deal.entity.DealImage;
import com.lootfast.backend.deal.entity.DealImageStatus;
import com.lootfast.backend.deal.entity.DealStatus;
import com.lootfast.backend.deal.repository.DealImageRepository;
import com.lootfast.backend.deal.repository.DealRepository;
import com.lootfast.backend.merchant.entity.MerchantOpeningHour;
import com.lootfast.backend.merchant.entity.MerchantProfile;
import com.lootfast.backend.merchant.repository.MerchantOpeningHourRepository;
import com.lootfast.backend.merchant.repository.MerchantProfileRepository;
import com.lootfast.backend.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MerchantDealService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Duration ORDER_CUTOFF = Duration.ofMinutes(15);
    private static final Set<DealStatus> PUBLISHED_STATUSES = Set.of(
            DealStatus.ACTIVE,
            DealStatus.HIDDEN,
            DealStatus.SOLD_OUT
    );

    private final MerchantDealAccess access;
    private final MerchantProfileRepository merchantRepository;
    private final MerchantOpeningHourRepository openingHourRepository;
    private final DealRepository dealRepository;
    private final DealImageRepository imageRepository;
    private final Clock clock;

    public MerchantDealService(
            MerchantDealAccess access,
            MerchantProfileRepository merchantRepository,
            MerchantOpeningHourRepository openingHourRepository,
            DealRepository dealRepository,
            DealImageRepository imageRepository,
            Clock clock
    ) {
        this.access = access;
        this.merchantRepository = merchantRepository;
        this.openingHourRepository = openingHourRepository;
        this.dealRepository = dealRepository;
        this.imageRepository = imageRepository;
        this.clock = clock;
    }

    @Transactional
    public MerchantDealResponse create(String subject, CreateMerchantDealRequest request) {
        User merchantUser = access.requireApprovedMerchant(subject);
        MerchantProfile merchant = findProfile(merchantUser.getId());
        NormalizedDeal normalized = validateAndNormalize(request);
        Deal deal = dealRepository.saveAndFlush(new Deal(
                merchant,
                normalized.name(),
                normalized.categoryCode(),
                normalized.originalPrice(),
                normalized.discountedPrice(),
                normalized.totalQuantity(),
                null,
                normalized.preparationMinutes(),
                normalized.pickupStartsAt(),
                normalized.pickupEndsAt(),
                normalized.saleStartsAt(),
                normalized.saleEndsAt(),
                DealStatus.DRAFT
        ));
        return MerchantDealResponse.from(deal, List.of());
    }

    @Transactional
    public MerchantDealResponse update(
            String subject,
            UUID dealId,
            CreateMerchantDealRequest request
    ) {
        User merchantUser = access.requireApprovedMerchant(subject);
        merchantRepository.findByUserIdForUpdate(merchantUser.getId())
                .orElseThrow(this::profileNotFound);
        Deal deal = findOwnedForUpdate(dealId, merchantUser.getId());
        if (deal.getStatus() != DealStatus.DRAFT) {
            throw new ApiException(
                    ErrorCode.DEAL_NOT_EDITABLE,
                    "Only draft deals can be edited"
            );
        }
        NormalizedDeal normalized = validateAndNormalize(request);
        deal.updateDraft(
                normalized.name(),
                normalized.categoryCode(),
                normalized.originalPrice(),
                normalized.discountedPrice(),
                normalized.totalQuantity(),
                normalized.preparationMinutes(),
                normalized.pickupStartsAt(),
                normalized.pickupEndsAt(),
                normalized.saleStartsAt(),
                normalized.saleEndsAt()
        );
        dealRepository.flush();
        return response(deal);
    }

    @Transactional
    public MerchantDealResponse publish(String subject, UUID dealId) {
        User merchantUser = access.requireApprovedMerchant(subject);
        MerchantProfile merchant = merchantRepository.findByUserIdForUpdate(merchantUser.getId())
                .orElseThrow(this::profileNotFound);
        Deal deal = findOwnedForUpdate(dealId, merchantUser.getId());
        if (deal.getStatus() != DealStatus.DRAFT) {
            throw new ApiException(
                    ErrorCode.DEAL_NOT_PUBLISHABLE,
                    "Only draft deals can be published"
            );
        }

        requireCompleteProfile(merchant);
        validateSchedule(
                deal.getAvailableFrom(),
                deal.getAvailableUntil(),
                deal.getPickupStart(),
                deal.getPickupEnd()
        );
        Instant now = clock.instant();
        if (!now.isBefore(deal.getAvailableUntil()) || !now.isBefore(deal.getPickupStart())) {
            throw new ApiException(
                    ErrorCode.INVALID_DEAL_SCHEDULE,
                    "Sale and pickup windows must still be in the future"
            );
        }
        requirePickupInsideOpeningHours(merchant, deal);
        requireSharedDailySchedule(merchant, deal);

        List<DealImage> images = activeImages(deal.getId());
        if (images.isEmpty()) {
            throw new ApiException(
                    ErrorCode.DEAL_IMAGE_REQUIRED,
                    "At least one deal image is required before publishing"
            );
        }
        deal.publish(images.getFirst().getImageUrl());
        dealRepository.flush();
        return MerchantDealResponse.from(deal, images);
    }

    @Transactional
    public MerchantDealResponse updateQuantity(
            String subject,
            UUID dealId,
            UpdateDealQuantityRequest request
    ) {
        User merchantUser = access.requireApprovedMerchant(subject);
        merchantRepository.findByUserIdForUpdate(merchantUser.getId())
                .orElseThrow(this::profileNotFound);
        Deal deal = findOwnedForUpdate(dealId, merchantUser.getId());
        if (deal.getStatus() == DealStatus.REMOVED) {
            throw new ApiException(ErrorCode.DEAL_NOT_EDITABLE, "Removed deals cannot be updated");
        }
        if (deal.getVersion() != request.expectedVersion()) {
            throw new ApiException(
                    ErrorCode.DEAL_VERSION_CONFLICT,
                    "Deal was updated by another request; reload and retry"
            );
        }
        int committed = deal.getTotalQuantity() - deal.getRemainingQuantity();
        if (request.totalQuantity() < committed) {
            throw new ApiException(
                    ErrorCode.DEAL_QUANTITY_CONFLICT,
                    "Total quantity cannot be lower than the committed quantity"
            );
        }
        deal.changeTotalQuantity(request.totalQuantity(), clock.instant());
        dealRepository.flush();
        return response(deal);
    }

    @Transactional(readOnly = true)
    public MerchantDealResponse get(String subject, UUID dealId) {
        User merchantUser = access.requireApprovedMerchant(subject);
        Deal deal = dealRepository.findOwned(dealId, merchantUser.getId())
                .orElseThrow(this::dealNotFound);
        return response(deal);
    }

    @Transactional(readOnly = true)
    public PagedResponse<MerchantDealResponse> list(
            String subject,
            int page,
            int size
    ) {
        User merchantUser = access.requireApprovedMerchant(subject);
        Page<Deal> deals = dealRepository.findMerchantDeals(
                merchantUser.getId(),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        Map<Long, List<DealImage>> imagesByDeal = imagesByDeal(deals.getContent());
        List<MerchantDealResponse> items = deals.getContent().stream()
                .map(deal -> MerchantDealResponse.from(
                        deal,
                        imagesByDeal.getOrDefault(deal.getId(), List.of())
                ))
                .toList();
        return new PagedResponse<>(
                items,
                deals.getNumber(),
                deals.getSize(),
                deals.getTotalElements(),
                deals.getTotalPages()
        );
    }

    private NormalizedDeal validateAndNormalize(CreateMerchantDealRequest request) {
        BigDecimal originalPrice = money(request.originalPrice());
        BigDecimal discountedPrice = money(request.discountedPrice());
        if (discountedPrice.multiply(BigDecimal.valueOf(100))
                .compareTo(originalPrice.multiply(BigDecimal.valueOf(90))) > 0) {
            throw new ApiException(
                    ErrorCode.INVALID_DEAL_PRICE,
                    "Discounted price must be at least 10% below the original price"
            );
        }
        validateSchedule(
                request.saleStartsAt(),
                request.saleEndsAt(),
                request.pickupStartsAt(),
                request.pickupEndsAt()
        );
        return new NormalizedDeal(
                request.name().trim(),
                request.categoryCode().trim().toUpperCase(java.util.Locale.ROOT),
                originalPrice,
                discountedPrice,
                request.totalQuantity(),
                request.preparationMinutes(),
                request.saleStartsAt(),
                request.saleEndsAt(),
                request.pickupStartsAt(),
                request.pickupEndsAt()
        );
    }

    private BigDecimal money(BigDecimal value) {
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new ApiException(ErrorCode.INVALID_DEAL_PRICE, "Prices support at most 2 decimals");
        }
    }

    private void validateSchedule(
            Instant saleStart,
            Instant saleEnd,
            Instant pickupStart,
            Instant pickupEnd
    ) {
        if (!saleStart.isBefore(saleEnd)
                || !pickupStart.isBefore(pickupEnd)
                || saleEnd.isAfter(pickupStart.minus(ORDER_CUTOFF))) {
            throw new ApiException(
                    ErrorCode.INVALID_DEAL_SCHEDULE,
                    "Sale must end at least 15 minutes before pickup starts"
            );
        }
        LocalDate businessDate = saleStart.atZone(BUSINESS_ZONE).toLocalDate();
        if (!businessDate.equals(saleEnd.atZone(BUSINESS_ZONE).toLocalDate())
                || !businessDate.equals(pickupStart.atZone(BUSINESS_ZONE).toLocalDate())
                || !businessDate.equals(pickupEnd.atZone(BUSINESS_ZONE).toLocalDate())) {
            throw new ApiException(
                    ErrorCode.INVALID_DEAL_SCHEDULE,
                    "Sale and pickup windows must be within one Vietnam calendar day"
            );
        }
    }

    private void requireCompleteProfile(MerchantProfile merchant) {
        if (isBlank(merchant.getStoreName())
                || isBlank(merchant.getAddressLine())
                || isBlank(merchant.getCity())
                || merchant.getLatitude() == null
                || merchant.getLongitude() == null) {
            throw new ApiException(
                    ErrorCode.MERCHANT_PROFILE_INCOMPLETE,
                    "Store information and location must be completed before publishing"
            );
        }
    }

    private void requirePickupInsideOpeningHours(MerchantProfile merchant, Deal deal) {
        List<MerchantOpeningHour> hours = openingHourRepository
                .findAllByMerchantProfileId(merchant.getId());
        LocalTime pickupStart = deal.getPickupStart().atZone(BUSINESS_ZONE).toLocalTime();
        LocalTime pickupEnd = deal.getPickupEnd().atZone(BUSINESS_ZONE).toLocalTime();
        var day = deal.getPickupStart().atZone(BUSINESS_ZONE).getDayOfWeek();
        boolean contained = hours.stream().anyMatch(slot ->
                slot.getDayOfWeek() == day
                        && !pickupStart.isBefore(slot.getOpensAt())
                        && !pickupEnd.isAfter(slot.getClosesAt())
        );
        if (!contained) {
            throw new ApiException(
                    ErrorCode.DEAL_OUTSIDE_OPENING_HOURS,
                    "Pickup window must be inside one store opening-hour slot"
            );
        }
    }

    private void requireSharedDailySchedule(MerchantProfile merchant, Deal deal) {
        LocalDate date = deal.getAvailableFrom().atZone(BUSINESS_ZONE).toLocalDate();
        Instant dayStart = date.atStartOfDay(BUSINESS_ZONE).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant();
        if (dealRepository.countScheduleConflicts(
                merchant.getId(),
                deal.getPublicId(),
                PUBLISHED_STATUSES,
                dayStart,
                dayEnd,
                deal.getAvailableFrom(),
                deal.getAvailableUntil(),
                deal.getPickupStart(),
                deal.getPickupEnd()
        ) > 0) {
            throw new ApiException(
                    ErrorCode.STORE_SCHEDULE_CONFLICT,
                    "All store deals for a day must share one sale and one pickup window"
            );
        }
    }

    private Map<Long, List<DealImage>> imagesByDeal(Collection<Deal> deals) {
        if (deals.isEmpty()) {
            return Map.of();
        }
        return imageRepository.findAllActiveByDealIds(
                        deals.stream().map(Deal::getId).toList(),
                        DealImageStatus.ACTIVE
                ).stream()
                .collect(Collectors.groupingBy(image -> image.getDeal().getId()));
    }

    private MerchantDealResponse response(Deal deal) {
        return MerchantDealResponse.from(deal, activeImages(deal.getId()));
    }

    private List<DealImage> activeImages(Long dealId) {
        return imageRepository.findAllByDealIdAndStatusOrderByDisplayOrderAscIdAsc(
                dealId,
                DealImageStatus.ACTIVE
        );
    }

    private Deal findOwnedForUpdate(UUID dealId, Long merchantUserId) {
        return dealRepository.findOwnedForUpdate(dealId, merchantUserId)
                .orElseThrow(this::dealNotFound);
    }

    private MerchantProfile findProfile(Long userId) {
        return merchantRepository.findByUserId(userId).orElseThrow(this::profileNotFound);
    }

    private ApiException dealNotFound() {
        return new ApiException(ErrorCode.DEAL_NOT_FOUND, "Deal was not found");
    }

    private ApiException profileNotFound() {
        return new ApiException(ErrorCode.MERCHANT_PROFILE_NOT_FOUND, "Merchant profile was not found");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record NormalizedDeal(
            String name,
            String categoryCode,
            BigDecimal originalPrice,
            BigDecimal discountedPrice,
            int totalQuantity,
            int preparationMinutes,
            Instant saleStartsAt,
            Instant saleEndsAt,
            Instant pickupStartsAt,
            Instant pickupEndsAt
    ) {
    }
}
