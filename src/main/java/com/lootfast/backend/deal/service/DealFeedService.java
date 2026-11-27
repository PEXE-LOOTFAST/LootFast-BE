package com.lootfast.backend.deal.service;

import com.lootfast.backend.common.exception.ApiException;
import com.lootfast.backend.common.exception.ErrorCode;
import com.lootfast.backend.common.response.PagedResponse;
import com.lootfast.backend.common.text.SearchNormalizer;
import com.lootfast.backend.deal.demo.DemoDeal;
import com.lootfast.backend.deal.demo.DemoDealGenerator;
import com.lootfast.backend.deal.dto.DealDiscoveryQuery;
import com.lootfast.backend.deal.dto.DealFeedItemResponse;
import com.lootfast.backend.deal.entity.Deal;
import com.lootfast.backend.deal.entity.DealStatus;
import com.lootfast.backend.deal.repository.DealRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

@Service
public class DealFeedService {

    private final DealRepository dealRepository;
    private final DealResponseMapper responseMapper;
    private final DemoDealGenerator demoDealGenerator;
    private final Clock clock;

    public DealFeedService(
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
        validatePickupWindow(query.pickupFrom(), query.pickupTo());
        Instant now = clock.instant();
        String categoryCode = normalizeCategory(query.categoryCode());
        String searchTerm = SearchNormalizer.normalize(query.keyword());
        GeoDistance.GeoBounds bounds = GeoDistance.bounds(
                query.latitude(),
                query.longitude(),
                query.radiusKm()
        );
        List<LocatedDeal> locatedDeals = dealRepository.findFeedCandidates(
                        DealStatus.ACTIVE,
                        now,
                        decimal(bounds.minLatitude()),
                        decimal(bounds.maxLatitude()),
                        decimal(bounds.minLongitude()),
                        decimal(bounds.maxLongitude()),
                        bounds.crossesDateLine(),
                        categoryCode,
                        query.minDiscountPercent(),
                        query.pickupFrom(),
                        query.pickupTo(),
                        searchTerm
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

        // Demo deals only fill an otherwise empty area, so they disappear on their own as soon as
        // a real merchant publishes a matching deal nearby.
        if (locatedDeals.isEmpty() && demoDealGenerator.enabled()) {
            List<LocatedDemoDeal> demoDeals = demoDeals(query, categoryCode, searchTerm, now);
            return page(demoDeals, query, located -> responseMapper.toFeedItem(
                    located.deal(),
                    kilometers(located.distanceKm())
            ));
        }
        return page(locatedDeals, query, located -> responseMapper.toFeedItem(
                located.deal(),
                kilometers(located.distanceKm())
        ));
    }

    /** Applies the same filters as the database query to the generated demo deals. */
    private List<LocatedDemoDeal> demoDeals(
            DealDiscoveryQuery query,
            String categoryCode,
            String searchTerm,
            Instant now
    ) {
        Integer minDiscountPercent = query.minDiscountPercent();
        Instant pickupFrom = query.pickupFrom();
        Instant pickupTo = query.pickupTo();
        return demoDealGenerator.around(query.latitude(), query.longitude(), now).stream()
                .filter(deal -> categoryCode == null || categoryCode.equals(deal.categoryCode()))
                .filter(deal -> minDiscountPercent == null || deal.originalPrice()
                        .subtract(deal.discountedPrice())
                        .multiply(BigDecimal.valueOf(100))
                        .compareTo(deal.originalPrice()
                                .multiply(BigDecimal.valueOf(minDiscountPercent))) >= 0)
                .filter(deal -> pickupFrom == null || deal.pickupEnd().isAfter(pickupFrom))
                .filter(deal -> pickupTo == null || deal.pickupStart().isBefore(pickupTo))
                .filter(deal -> searchTerm == null
                        || SearchNormalizer.normalize(deal.name()).contains(searchTerm)
                        || SearchNormalizer.normalize(deal.storeName()).contains(searchTerm))
                .map(deal -> new LocatedDemoDeal(
                        deal,
                        GeoDistance.rawKilometers(
                                query.latitude(),
                                query.longitude(),
                                deal.latitude(),
                                deal.longitude()
                        )
                ))
                .filter(located -> located.distanceKm() <= query.radiusKm())
                .sorted(Comparator.comparingDouble(LocatedDemoDeal::distanceKm)
                        .thenComparing(located -> located.deal().id()))
                .limit(demoDealGenerator.maxPerFeed())
                .toList();
    }

    private static <T> PagedResponse<DealFeedItemResponse> page(
            List<T> sorted,
            DealDiscoveryQuery query,
            Function<T, DealFeedItemResponse> mapper
    ) {
        long requestedOffset = (long) query.page() * query.size();
        int fromIndex = requestedOffset >= sorted.size()
                ? sorted.size()
                : (int) requestedOffset;
        int toIndex = Math.min(fromIndex + query.size(), sorted.size());
        List<DealFeedItemResponse> items = sorted.subList(fromIndex, toIndex)
                .stream()
                .map(mapper)
                .toList();
        int totalPages = sorted.isEmpty()
                ? 0
                : (int) Math.ceil((double) sorted.size() / query.size());
        return new PagedResponse<>(
                items,
                query.page(),
                query.size(),
                sorted.size(),
                totalPages
        );
    }

    private static BigDecimal kilometers(double distanceKm) {
        return BigDecimal.valueOf(distanceKm).setScale(3, RoundingMode.HALF_UP);
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

    private record LocatedDemoDeal(DemoDeal deal, double distanceKm) {
    }
}
