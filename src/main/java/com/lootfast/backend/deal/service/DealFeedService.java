package com.lootfast.backend.deal.service;

import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.common.response.PagedResponse;
import com.lootfast.backend.common.text.SearchNormalizer;
import com.lootfast.backend.deal.dto.DealDiscoveryQuery;
import com.lootfast.backend.deal.dto.DealFeedItemResponse;
import com.lootfast.backend.deal.entity.Deal;
import com.lootfast.backend.deal.entity.DealStatus;
import com.lootfast.backend.deal.repository.DealRepository;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class DealFeedService {

    private final DealRepository dealRepository;
    private final DealResponseMapper responseMapper;
    private final Clock clock;

    public DealFeedService(
            DealRepository dealRepository,
            DealResponseMapper responseMapper,
            Clock clock
    ) {
        this.dealRepository = dealRepository;
        this.responseMapper = responseMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PagedResponse<DealFeedItemResponse> getFeed(
            double latitude,
            double longitude,
            double radiusKm,
            int page,
            int size
    ) {
        return getFeed(new DealDiscoveryQuery(
                latitude,
                longitude,
                radiusKm,
                null,
                null,
                null,
                null,
                null,
                page,
                size
        ));
    }

    @Transactional(readOnly = true)
    public PagedResponse<DealFeedItemResponse> getFeed(DealDiscoveryQuery query) {
        validateQuery(query);
        validatePickupWindow(query.pickupFrom(), query.pickupTo());
        if (query.latitude() == null) {
            var page = dealRepository.findGlobalFeed(
                    DealStatus.ACTIVE, clock.instant(), normalizeCategory(query.categoryCode()),
                    query.minDiscountPercent(), query.pickupFrom(), query.pickupTo(),
                    SearchNormalizer.normalize(query.keyword()),
                    PageRequest.of(query.page(), query.size(),
                            Sort.by(Sort.Direction.DESC, "createdAt", "id")));
            return PagedResponse.from(page.map(deal -> responseMapper.toFeedItem(deal, null)));
        }
        GeoDistance.GeoBounds bounds = GeoDistance.bounds(
                query.latitude(),
                query.longitude(),
                query.radiusKm()
        );
        List<LocatedDeal> locatedDeals = dealRepository.findFeedCandidates(
                        DealStatus.ACTIVE,
                        clock.instant(),
                        decimal(bounds.minLatitude()),
                        decimal(bounds.maxLatitude()),
                        decimal(bounds.minLongitude()),
                        decimal(bounds.maxLongitude()),
                        bounds.crossesDateLine(),
                        normalizeCategory(query.categoryCode()),
                        query.minDiscountPercent(),
                        query.pickupFrom(),
                        query.pickupTo(),
                        SearchNormalizer.normalize(query.keyword())
                ).stream()
                .map(deal -> new LocatedDeal(
                        deal,
                        GeoDistance.rawKilometers(
                                query.latitude(),
                                query.longitude(),
                                deal.getMerchant().getLatitude().doubleValue(),
                                deal.getMerchant().getLongitude().doubleValue()
                        )
                ))
                .filter(located -> located.distanceKm() <= query.radiusKm())
                .sorted(Comparator.comparingDouble(LocatedDeal::distanceKm)
                        .thenComparing(located -> located.deal().getPublicId()))
                .toList();

        long requestedOffset = (long) query.page() * query.size();
        int fromIndex = requestedOffset >= locatedDeals.size()
                ? locatedDeals.size()
                : (int) requestedOffset;
        int toIndex = Math.min(fromIndex + query.size(), locatedDeals.size());
        List<DealFeedItemResponse> items = locatedDeals.subList(fromIndex, toIndex)
                .stream()
                .map(this::toResponse)
                .toList();
        int totalPages = locatedDeals.isEmpty()
                ? 0
                : (int) Math.ceil((double) locatedDeals.size() / query.size());
        return new PagedResponse<>(
                items,
                query.page(),
                query.size(),
                locatedDeals.size(),
                totalPages
        );
    }

    private DealFeedItemResponse toResponse(LocatedDeal located) {
        return responseMapper.toFeedItem(
                located.deal(),
                BigDecimal.valueOf(located.distanceKm()).setScale(3, RoundingMode.HALF_UP)
        );
    }

    private void validateQuery(DealDiscoveryQuery query) {
        if ((query.latitude() == null) != (query.longitude() == null)) {
            throw new ApiException(ErrorCode.INVALID_DEAL_FILTER,
                    "latitude and longitude must both be provided or both omitted");
        }
        if (query.page() < 0 || query.size() < 1 || query.size() > 50
                || (long) query.page() * query.size() > Integer.MAX_VALUE) {
            throw new ApiException(ErrorCode.INVALID_DEAL_FILTER, "Invalid pagination; size must be 1 to 50");
        }
        if (query.latitude() != null && (!Double.isFinite(query.latitude()) || !Double.isFinite(query.longitude())
                || query.latitude() < -90 || query.latitude() > 90
                || query.longitude() < -180 || query.longitude() > 180)) {
            throw new ApiException(ErrorCode.INVALID_DEAL_FILTER, "Invalid coordinates");
        }
        if (!Double.isFinite(query.radiusKm()) || query.radiusKm() < 0.1 || query.radiusKm() > 50) {
            throw new ApiException(ErrorCode.INVALID_DEAL_FILTER, "radiusKm must be 0.1 to 50");
        }
    }

    private void validatePickupWindow(Instant pickupFrom, Instant pickupTo) {
        if (pickupFrom != null && pickupTo != null && !pickupFrom.isBefore(pickupTo)) {
            throw new ApiException(
                    ErrorCode.INVALID_DEAL_FILTER,
                    "pickupFrom must be before pickupTo"
            );
        }
    }

    private String normalizeCategory(String categoryCode) {
        if (categoryCode == null || categoryCode.isBlank()) {
            return null;
        }
        return categoryCode.trim().toUpperCase(Locale.ROOT);
    }

    private BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP);
    }

    private record LocatedDeal(Deal deal, double distanceKm) {
    }
}
