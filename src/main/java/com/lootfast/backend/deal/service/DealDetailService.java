package com.lootfast.backend.deal.service;

import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.deal.dto.DealDetailResponse;
import com.lootfast.backend.deal.entity.Deal;
import com.lootfast.backend.deal.entity.DealStatus;
import com.lootfast.backend.deal.repository.DealRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;

@Service
public class DealDetailService {

    private static final Set<DealStatus> PUBLIC_STATUSES = Set.of(
            DealStatus.ACTIVE,
            DealStatus.SOLD_OUT
    );

    private final DealRepository dealRepository;
    private final DealResponseMapper responseMapper;
    private final Clock clock;

    public DealDetailService(
            DealRepository dealRepository,
            DealResponseMapper responseMapper,
            Clock clock
    ) {
        this.dealRepository = dealRepository;
        this.responseMapper = responseMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DealDetailResponse getDetail(UUID dealId, Double latitude, Double longitude) {
        validateCoordinates(latitude, longitude);
        Deal deal = dealRepository.findPublicDeal(dealId, PUBLIC_STATUSES, clock.instant())
                .orElseThrow(() -> new ApiException(
                        ErrorCode.DEAL_NOT_FOUND,
                        "Deal is not available"
                ));
        BigDecimal distance = latitude == null
                ? null
                : GeoDistance.kilometers(
                        latitude,
                        longitude,
                        deal.getMerchant().getLatitude().doubleValue(),
                        deal.getMerchant().getLongitude().doubleValue()
                );
        return responseMapper.toDetail(deal, distance);
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
