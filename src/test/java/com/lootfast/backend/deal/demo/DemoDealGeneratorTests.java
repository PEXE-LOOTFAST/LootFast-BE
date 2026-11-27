package com.lootfast.backend.deal.demo;

import com.lootfast.backend.deal.service.GeoDistance;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoDealGeneratorTests {

    /** 11:00 in Ho Chi Minh City. */
    private static final Instant MORNING = Instant.parse("2026-09-15T04:00:00Z");
    /** 22:30 in Ho Chi Minh City, after every same-day demo pickup window has ended. */
    private static final Instant LATE_NIGHT = Instant.parse("2026-09-15T15:30:00Z");
    private static final double HCMC_LATITUDE = 10.841_100;
    private static final double HCMC_LONGITUDE = 106.809_900;

    private final DemoDealGenerator generator = new DemoDealGenerator(
            new DemoDealProperties(true, 2, ZoneId.of("Asia/Ho_Chi_Minh"))
    );

    @Test
    void sameAreaAndTimeAlwaysProduceTheSameDeals() {
        DemoDealGenerator otherInstance = new DemoDealGenerator(
                new DemoDealProperties(true, 2, ZoneId.of("Asia/Ho_Chi_Minh"))
        );

        assertEquals(
                generator.around(HCMC_LATITUDE, HCMC_LONGITUDE, MORNING),
                otherInstance.around(HCMC_LATITUDE, HCMC_LONGITUDE, MORNING)
        );
    }

    @Test
    void aDemoDealIsAlwaysWithinOneCellDiagonalOfTheBuyer() {
        Random points = new Random(42);
        for (int i = 0; i < 300; i++) {
            double latitude = 8.5 + points.nextDouble() * 15;
            double longitude = 102 + points.nextDouble() * 7.5;
            for (Instant now : List.of(MORNING, LATE_NIGHT)) {
                double nearest = generator.around(latitude, longitude, now).stream()
                        .map(deal -> distance(latitude, longitude, deal))
                        .min(Double::compare)
                        .orElse(Double.MAX_VALUE);

                assertTrue(nearest <= 0.8, "nearest demo deal was " + nearest + " km away");
            }
        }
    }

    @Test
    void generatedDealsAreVisiblePlausibleAndClearlyLabelled() {
        Set<UUID> ids = new HashSet<>();
        for (DemoDeal deal : generator.around(HCMC_LATITUDE, HCMC_LONGITUDE, MORNING)) {
            assertTrue(ids.add(deal.id()), "demo ids must be unique");
            assertTrue(deal.visibleAt(MORNING));
            assertTrue(deal.discountedPrice().compareTo(BigDecimal.ZERO) > 0);
            assertTrue(deal.discountedPrice().compareTo(deal.originalPrice()) < 0);
            assertTrue(deal.pickupStart().isBefore(deal.pickupEnd()));
            assertTrue(deal.storeName().startsWith("[DEMO] "));
            assertTrue(deal.name().endsWith("(deal minh họa)"));
            assertTrue(Set.of("BAKERY", "MEAL", "VEGETARIAN", "DRINK").contains(deal.categoryCode()));
        }
        assertFalse(ids.isEmpty());
    }

    @Test
    void detailLookupRegeneratesTheSameDealFromItsId() {
        DemoDeal listed = generator.around(HCMC_LATITUDE, HCMC_LONGITUDE, MORNING).getFirst();

        assertEquals(listed, generator.findVisible(listed.id(), MORNING).orElseThrow());
        assertTrue(generator.findVisible(listed.storeId(), MORNING).isEmpty());
        assertTrue(generator.findVisible(listed.id(), listed.pickupEnd()).isEmpty());
        assertTrue(generator.findVisible(UUID.randomUUID(), MORNING).isEmpty());
    }

    @Test
    void cellsAreClampedAtThePolesAndTheDateLine() {
        assertEquals(17_999, DemoDealGenerator.latitudeCell(90));
        assertEquals(-18_000, DemoDealGenerator.latitudeCell(-90));
        assertEquals(35_999, DemoDealGenerator.longitudeCell(180));
        assertFalse(generator.around(0, 179.999, MORNING).isEmpty());
        assertFalse(generator.around(89.999, 0, MORNING).isEmpty());
    }

    private static double distance(double latitude, double longitude, DemoDeal deal) {
        return GeoDistance.kilometers(latitude, longitude, deal.latitude(), deal.longitude())
                .doubleValue();
    }
}
