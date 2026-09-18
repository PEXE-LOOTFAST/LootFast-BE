package com.lootfast.backend.deal.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class GeoDistance {

    private static final double EARTH_RADIUS_KM = 6_371.0088;
    private static final double KM_PER_LATITUDE_DEGREE = 111.32;

    private GeoDistance() {
    }

    public static BigDecimal kilometers(
            double fromLatitude,
            double fromLongitude,
            double toLatitude,
            double toLongitude
    ) {
        return BigDecimal.valueOf(rawKilometers(
                fromLatitude,
                fromLongitude,
                toLatitude,
                toLongitude
        )).setScale(3, RoundingMode.HALF_UP);
    }

    static double rawKilometers(
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

    static GeoBounds bounds(double latitude, double longitude, double radiusKm) {
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
        return new GeoBounds(
                minLatitude,
                maxLatitude,
                normalizeLongitude(rawMinLongitude),
                normalizeLongitude(rawMaxLongitude),
                rawMinLongitude < -180 || rawMaxLongitude > 180
        );
    }

    private static double normalizeLongitude(double longitude) {
        double normalized = longitude;
        while (normalized < -180) {
            normalized += 360;
        }
        while (normalized > 180) {
            normalized -= 360;
        }
        return normalized;
    }

    record GeoBounds(
            double minLatitude,
            double maxLatitude,
            double minLongitude,
            double maxLongitude,
            boolean crossesDateLine
    ) {
    }
}
