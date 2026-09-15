package com.lootfast.backend.deal.service;

import com.lootfast.backend.common.response.PagedResponse;
import com.lootfast.backend.deal.dto.DealFeedItemResponse;
import com.lootfast.backend.deal.entity.Deal;
import com.lootfast.backend.deal.entity.DealStatus;
import com.lootfast.backend.deal.repository.DealRepository;
import com.lootfast.backend.merchant.entity.MerchantProfile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;

@Service
public class DealFeedService {

    private static final double EARTH_RADIUS_KM = 6_371.0088;
    private static final double KM_PER_LATITUDE_DEGREE = 111.32;

    private final DealRepository dealRepository;
    private final Clock clock;

    public DealFeedService(DealRepository dealRepository, Clock clock) {
        this.dealRepository = dealRepository;
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
        GeoBounds bounds = bounds(latitude, longitude, radiusKm);
        List<LocatedDeal> locatedDeals = dealRepository.findFeedCandidates(
                        DealStatus.ACTIVE,
                        clock.instant(),
                        decimal(bounds.minLatitude()),
                        decimal(bounds.maxLatitude()),
                        decimal(bounds.minLongitude()),
                        decimal(bounds.maxLongitude()),
                        bounds.crossesDateLine()
                ).stream()
                .map(deal -> new LocatedDeal(
                        deal,
                        distanceKm(
                                latitude,
                                longitude,
                                deal.getMerchant().getLatitude().doubleValue(),
                                deal.getMerchant().getLongitude().doubleValue()
                        )
                ))
                .filter(located -> located.distanceKm() <= radiusKm)
                .sorted(Comparator.comparingDouble(LocatedDeal::distanceKm)
                        .thenComparing(located -> located.deal().getPublicId()))
                .toList();

        long requestedOffset = (long) page * size;
        int fromIndex = requestedOffset >= locatedDeals.size()
                ? locatedDeals.size()
                : (int) requestedOffset;
        int toIndex = Math.min(fromIndex + size, locatedDeals.size());
        List<DealFeedItemResponse> items = locatedDeals.subList(fromIndex, toIndex)
                .stream()
                .map(this::toResponse)
                .toList();
        int totalPages = locatedDeals.isEmpty()
                ? 0
                : (int) Math.ceil((double) locatedDeals.size() / size);
        return new PagedResponse<>(items, page, size, locatedDeals.size(), totalPages);
    }

    private DealFeedItemResponse toResponse(LocatedDeal located) {
        Deal deal = located.deal();
        MerchantProfile merchant = deal.getMerchant();
        return new DealFeedItemResponse(
                deal.getPublicId(),
                deal.getName(),
                deal.getCategoryCode(),
                deal.getOriginalPrice(),
                deal.getDiscountedPrice(),
                discountPercent(deal),
                deal.getRemainingQuantity(),
                deal.getPrimaryImageUrl(),
                deal.getPreparationMinutes(),
                deal.getPickupStart(),
                deal.getPickupEnd(),
                BigDecimal.valueOf(located.distanceKm()).setScale(3, RoundingMode.HALF_UP),
                new DealFeedItemResponse.MerchantSummary(
                        merchant.getUser().getPublicId(),
                        merchant.getStoreName(),
                        merchant.getAddressLine(),
                        merchant.getWard(),
                        merchant.getDistrict(),
                        merchant.getCity(),
                        true
                )
        );
    }

    private int discountPercent(Deal deal) {
        return deal.getOriginalPrice()
                .subtract(deal.getDiscountedPrice())
                .multiply(BigDecimal.valueOf(100))
                .divide(deal.getOriginalPrice(), 0, RoundingMode.HALF_UP)
                .intValueExact();
    }

    private GeoBounds bounds(double latitude, double longitude, double radiusKm) {
        double latitudeDelta = radiusKm / KM_PER_LATITUDE_DEGREE;
        double minLatitude = Math.max(-90, latitude - latitudeDelta);
        double maxLatitude = Math.min(90, latitude + latitudeDelta);

        double furthestLatitude = Math.max(Math.abs(minLatitude), Math.abs(maxLatitude));
        double cosine = Math.cos(Math.toRadians(furthestLatitude));
        double longitudeDelta = Math.abs(cosine) < 0.000_001
                ? 180
                : Math.min(180, radiusKm / (KM_PER_LATITUDE_DEGREE * Math.abs(cosine)));
        if (longitudeDelta >= 180) {
            return new GeoBounds(minLatitude, maxLatitude, -180, 180, false);
        }

        double rawMinLongitude = longitude - longitudeDelta;
        double rawMaxLongitude = longitude + longitudeDelta;
        boolean crossesDateLine = rawMinLongitude < -180 || rawMaxLongitude > 180;
        double minLongitude = normalizeLongitude(rawMinLongitude);
        double maxLongitude = normalizeLongitude(rawMaxLongitude);
        return new GeoBounds(
                minLatitude,
                maxLatitude,
                minLongitude,
                maxLongitude,
                crossesDateLine
        );
    }

    private double normalizeLongitude(double longitude) {
        double normalized = longitude;
        while (normalized < -180) {
            normalized += 360;
        }
        while (normalized > 180) {
            normalized -= 360;
        }
        return normalized;
    }

    private double distanceKm(
            double fromLatitude,
            double fromLongitude,
            double toLatitude,
            double toLongitude
    ) {
        double latitudeDelta = Math.toRadians(toLatitude - fromLatitude);
        double longitudeDelta = Math.toRadians(toLongitude - fromLongitude);
        double fromLatitudeRadians = Math.toRadians(fromLatitude);
        double toLatitudeRadians = Math.toRadians(toLatitude);
        double haversine = Math.pow(Math.sin(latitudeDelta / 2), 2)
                + Math.cos(fromLatitudeRadians)
                * Math.cos(toLatitudeRadians)
                * Math.pow(Math.sin(longitudeDelta / 2), 2);
        haversine = Math.max(0, Math.min(1, haversine));
        return EARTH_RADIUS_KM * 2
                * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine));
    }

    private BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP);
    }

    private record LocatedDeal(Deal deal, double distanceKm) {
    }

    private record GeoBounds(
            double minLatitude,
            double maxLatitude,
            double minLongitude,
            double maxLongitude,
            boolean crossesDateLine
    ) {
    }
}
