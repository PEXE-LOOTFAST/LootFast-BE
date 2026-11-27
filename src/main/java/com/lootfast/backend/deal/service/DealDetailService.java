package com.lootfast.backend.deal.service;

import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.deal.demo.DemoDeal;
import com.lootfast.backend.deal.demo.DemoDealGenerator;
import com.lootfast.backend.deal.dto.DealDetailResponse;
import com.lootfast.backend.deal.entity.Deal;
import com.lootfast.backend.deal.entity.DealStatus;
import com.lootfast.backend.deal.repository.DealRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
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
    private final DemoDealGenerator demoDealGenerator;
    private final Clock clock;

    public DealDetailService(
            DealRepository dealRepository,
            DealResponseMapper responseMapper,
            DemoDealGenerator demoDealGenerator,
            Clock clock
    ) {
        this.dealRepository = dealRepository;
        this.responseMapper = responseMapper;
        this.demoDealGenerator = demoDealGenerator;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DealDetailResponse getDetail(UUID dealId, Double latitude, Double longitude) {
        validateCoordinates(latitude, longitude);
        Instant now = clock.instant();
        if (demoDealGenerator.enabled()) {
            // Demo ids are version-8 UUIDs that real deals never use, so they are recognised and
            // regenerated without a database lookup.
            Optional<DemoDeal> demoDeal = demoDealGenerator.findVisible(dealId, now);
            if (demoDeal.isPresent()) {
                DemoDeal demo = demoDeal.get();
                BigDecimal distance = latitude == null
                        ? null
                        : GeoDistance.kilometers(latitude, longitude, demo.latitude(), demo.longitude());
                return responseMapper.toDetail(demo, distance);
            }
        }
        Deal deal = dealRepository.findPublicDeal(dealId, PUBLIC_STATUSES, now)
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
