package com.lootfast.backend.merchant.service;

import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.common.response.PagedResponse;
import com.lootfast.backend.deal.dto.DealFeedItemResponse;
import com.lootfast.backend.deal.entity.DealStatus;
import com.lootfast.backend.deal.repository.DealRepository;
import com.lootfast.backend.deal.service.DealResponseMapper;
import com.lootfast.backend.deal.service.GeoDistance;
import com.lootfast.backend.merchant.dto.OpeningHourSlotResponse;
import com.lootfast.backend.merchant.dto.PublicStoreProfileResponse;
import com.lootfast.backend.merchant.entity.MerchantOpeningHour;
import com.lootfast.backend.merchant.entity.MerchantProfile;
import com.lootfast.backend.merchant.repository.MerchantOpeningHourRepository;
import com.lootfast.backend.merchant.repository.MerchantProfileRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class PublicStoreService {

    private static final Comparator<MerchantOpeningHour> OPENING_HOUR_ORDER =
            Comparator.comparing(MerchantOpeningHour::getDayOfWeek)
                    .thenComparing(MerchantOpeningHour::getOpensAt);

    private final MerchantProfileRepository merchantProfileRepository;
    private final MerchantOpeningHourRepository openingHourRepository;
    private final DealRepository dealRepository;
    private final DealResponseMapper dealResponseMapper;
    private final Clock clock;

    public PublicStoreService(
            MerchantProfileRepository merchantProfileRepository,
            MerchantOpeningHourRepository openingHourRepository,
            DealRepository dealRepository,
            DealResponseMapper dealResponseMapper,
            Clock clock
    ) {
        this.merchantProfileRepository = merchantProfileRepository;
        this.openingHourRepository = openingHourRepository;
        this.dealRepository = dealRepository;
        this.dealResponseMapper = dealResponseMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PublicStoreProfileResponse getStore(
            UUID merchantId,
            Double latitude,
            Double longitude,
            int dealPage,
            int dealSize
    ) {
        validateCoordinates(latitude, longitude);
        MerchantProfile profile = merchantProfileRepository
                .findPublicByUserPublicId(merchantId)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.MERCHANT_PROFILE_NOT_FOUND,
                        "Store is not available"
                ));
        List<OpeningHourSlotResponse> openingHours = openingHourRepository
                .findAllByMerchantProfileId(profile.getId())
                .stream()
                .sorted(OPENING_HOUR_ORDER)
                .map(OpeningHourSlotResponse::from)
                .toList();
        Page<DealFeedItemResponse> deals = dealRepository.findPublicStoreDeals(
                        profile.getId(),
                        DealStatus.ACTIVE,
                        clock.instant(),
                        PageRequest.of(
                                dealPage,
                                dealSize,
                                Sort.by("pickupEnd").ascending()
                                        .and(Sort.by("publicId").ascending())
                        )
                )
                .map(deal -> dealResponseMapper.toFeedItem(
                        deal,
                        distance(latitude, longitude, profile)
                ));

        return new PublicStoreProfileResponse(
                profile.getUser().getPublicId(),
                profile.getStoreName(),
                profile.getDescription(),
                profile.getBusinessCategory(),
                profile.getContactEmail(),
                profile.getContactPhone(),
                profile.getLogoUrl(),
                profile.getCoverUrl(),
                new PublicStoreProfileResponse.Address(
                        profile.getAddressLine(),
                        profile.getWard(),
                        profile.getDistrict(),
                        profile.getCity(),
                        profile.getLatitude(),
                        profile.getLongitude()
                ),
                true,
                new PublicStoreProfileResponse.RatingSummary(null, 0),
                openingHours,
                PagedResponse.from(deals)
        );
    }

    private BigDecimal distance(
            Double latitude,
            Double longitude,
            MerchantProfile profile
    ) {
        if (latitude == null) {
            return null;
        }
        return GeoDistance.kilometers(
                latitude,
                longitude,
                profile.getLatitude().doubleValue(),
                profile.getLongitude().doubleValue()
        );
    }

    private void validateCoordinates(Double latitude, Double longitude) {
        if ((latitude == null) != (longitude == null)) {
            throw new ApiException(
                    ErrorCode.INVALID_DEAL_FILTER,
                    "Latitude and longitude must be provided together"
            );
        }
    }
}
