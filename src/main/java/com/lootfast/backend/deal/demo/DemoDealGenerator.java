package com.lootfast.backend.deal.demo;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Deterministically generates demo deals on a fixed geographic grid.
 *
 * <p>The world is divided into cells of {@value #CELL_DEGREES} degrees (about 550 m in Vietnam).
 * Every cell holds one or two fictional stores per day, at positions derived from a seed made of
 * the cell and the date. The same area therefore shows the same demo deals to every buyer and on
 * every refresh, nothing has to be stored, and the work per request is bounded (3 x 3 cells x
 * 2 days x at most 2 deals). Because a buyer always stands inside a cell, the nearest demo store
 * is at most one cell diagonal (about 0.78 km) away.
 */
@Component
public class DemoDealGenerator {

    static final double CELL_DEGREES = 0.005;
    private static final int MIN_LATITUDE_CELL = -18_000;
    private static final int MAX_LATITUDE_CELL = 17_999;
    private static final int MIN_LONGITUDE_CELL = -36_000;
    private static final int MAX_LONGITUDE_CELL = 35_999;
    private static final int LONGITUDE_CELLS = MAX_LONGITUDE_CELL - MIN_LONGITUDE_CELL + 1;

    private static final int MAX_DEALS_PER_CELL = 2;
    /** Listed from 20:00 the evening before, so the feed is not empty late at night. */
    private static final LocalTime LISTED_FROM_PREVIOUS_DAY = LocalTime.of(20, 0);
    private static final LocalTime EARLIEST_PICKUP = LocalTime.of(17, 0);
    private static final Duration PICKUP_LENGTH = Duration.ofHours(4);
    private static final String DEMO_PREFIX = "[DEMO] ";
    private static final String DEMO_SUFFIX = " (deal minh họa)";

    private static final List<CatalogItem> CATALOG = List.of(
            new CatalogItem("Bánh mì thịt nướng", "Tiệm bánh mì mẫu", "BAKERY", 30_000),
            new CatalogItem("Combo bánh ngọt cuối ngày", "Tiệm bánh mẫu", "BAKERY", 80_000),
            new CatalogItem("Cơm tấm sườn bì chả", "Quán cơm tấm mẫu", "MEAL", 55_000),
            new CatalogItem("Bún bò Huế", "Quán bún mẫu", "MEAL", 50_000),
            new CatalogItem("Gà rán 3 miếng", "Tiệm gà rán mẫu", "MEAL", 75_000),
            new CatalogItem("Sushi set tối", "Cửa hàng sushi mẫu", "MEAL", 120_000),
            new CatalogItem("Cơm chay thập cẩm", "Quán chay mẫu", "VEGETARIAN", 40_000),
            new CatalogItem("Trà sữa trân châu", "Quán trà sữa mẫu", "DRINK", 35_000)
    );

    private final DemoDealProperties properties;

    public DemoDealGenerator(DemoDealProperties properties) {
        this.properties = properties;
    }

    public boolean enabled() {
        return properties.enabled();
    }

    public int maxPerFeed() {
        return properties.maxPerFeed();
    }

    /**
     * Demo deals visible at {@code now} whose store lies in the 3 x 3 cells around the point.
     * Callers still filter by exact distance and search criteria.
     */
    public List<DemoDeal> around(double latitude, double longitude, Instant now) {
        int latitudeCell = latitudeCell(latitude);
        int longitudeCell = longitudeCell(longitude);
        long today = LocalDate.ofInstant(now, zone()).toEpochDay();
        List<DemoDeal> visible = new ArrayList<>();
        for (long day = today; day <= today + 1; day++) {
            for (int latitudeOffset = -1; latitudeOffset <= 1; latitudeOffset++) {
                int cellLatitude = latitudeCell + latitudeOffset;
                if (cellLatitude < MIN_LATITUDE_CELL || cellLatitude > MAX_LATITUDE_CELL) {
                    continue;
                }
                for (int longitudeOffset = -1; longitudeOffset <= 1; longitudeOffset++) {
                    int cellLongitude = wrapLongitudeCell(longitudeCell + longitudeOffset);
                    for (DemoDeal deal : cellDeals(cellLatitude, cellLongitude, day)) {
                        if (deal.visibleAt(now)) {
                            visible.add(deal);
                        }
                    }
                }
            }
        }
        return visible;
    }

    /** Regenerates the demo deal behind {@code dealId}, if it is a demo id still visible now. */
    public Optional<DemoDeal> findVisible(UUID dealId, Instant now) {
        return DemoDealId.parse(dealId)
                .filter(id -> id.kind() == DemoDealId.Kind.DEAL)
                .filter(id -> id.latitudeCell() >= MIN_LATITUDE_CELL
                        && id.latitudeCell() <= MAX_LATITUDE_CELL
                        && id.longitudeCell() >= MIN_LONGITUDE_CELL
                        && id.longitudeCell() <= MAX_LONGITUDE_CELL)
                .flatMap(id -> cellDeals(id.latitudeCell(), id.longitudeCell(), id.epochDay())
                        .stream()
                        .filter(deal -> deal.id().equals(dealId))
                        .findFirst())
                .filter(deal -> deal.visibleAt(now));
    }

    List<DemoDeal> cellDeals(int latitudeCell, int longitudeCell, long epochDay) {
        // java.util.Random's algorithm is fixed by its specification, so a given seed yields the
        // same sequence on every JVM and every instance: ids regenerate identically everywhere.
        Random random = new Random(seed(latitudeCell, longitudeCell, epochDay));
        ZoneId zone = zone();
        LocalDate day = LocalDate.ofEpochDay(epochDay);
        Instant availableFrom = day.minusDays(1).atTime(LISTED_FROM_PREVIOUS_DAY)
                .atZone(zone).toInstant();
        int count = 1 + random.nextInt(MAX_DEALS_PER_CELL);
        List<DemoDeal> deals = new ArrayList<>(count);
        for (int slot = 0; slot < count; slot++) {
            double latitude = roundCoordinate((latitudeCell + random.nextDouble()) * CELL_DEGREES);
            double longitude = roundCoordinate((longitudeCell + random.nextDouble()) * CELL_DEGREES);
            CatalogItem item = CATALOG.get(random.nextInt(CATALOG.size()));
            int discountPercent = 30 + 5 * random.nextInt(7);
            int remainingQuantity = 2 + random.nextInt(7);
            int preparationMinutes = 5 + 5 * random.nextInt(3);
            Instant pickupStart = day.atTime(EARLIEST_PICKUP)
                    .plusMinutes(30L * random.nextInt(3))
                    .atZone(zone)
                    .toInstant();
            BigDecimal originalPrice = BigDecimal.valueOf(item.originalPrice());
            DemoDealId dealId = new DemoDealId(
                    DemoDealId.Kind.DEAL,
                    latitudeCell,
                    longitudeCell,
                    epochDay,
                    slot
            );
            deals.add(new DemoDeal(
                    dealId.toUuid(),
                    dealId.withKind(DemoDealId.Kind.STORE).toUuid(),
                    item.name() + DEMO_SUFFIX,
                    DEMO_PREFIX + item.storeName(),
                    item.categoryCode(),
                    originalPrice,
                    discountedPrice(originalPrice, discountPercent),
                    remainingQuantity,
                    preparationMinutes,
                    availableFrom,
                    pickupStart,
                    pickupStart.plus(PICKUP_LENGTH),
                    latitude,
                    longitude
            ));
        }
        return deals;
    }

    static int latitudeCell(double latitude) {
        int cell = (int) Math.floor(latitude / CELL_DEGREES);
        return Math.max(MIN_LATITUDE_CELL, Math.min(MAX_LATITUDE_CELL, cell));
    }

    static int longitudeCell(double longitude) {
        int cell = (int) Math.floor(longitude / CELL_DEGREES);
        return Math.max(MIN_LONGITUDE_CELL, Math.min(MAX_LONGITUDE_CELL, cell));
    }

    private static int wrapLongitudeCell(int cell) {
        return Math.floorMod(cell - MIN_LONGITUDE_CELL, LONGITUDE_CELLS) + MIN_LONGITUDE_CELL;
    }

    /** Rounded to 1000 VND so generated prices look like real menu prices. */
    private static BigDecimal discountedPrice(BigDecimal originalPrice, int discountPercent) {
        return originalPrice
                .multiply(BigDecimal.valueOf(100L - discountPercent))
                .divide(BigDecimal.valueOf(100_000), 0, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(1_000));
    }

    private static double roundCoordinate(double value) {
        return Math.round(value * 1_000_000d) / 1_000_000d;
    }

    /** SplitMix64-style mixing so neighbouring cells and days get unrelated sequences. */
    private static long seed(int latitudeCell, int longitudeCell, long epochDay) {
        long hash = 0x9E37_79B9_7F4A_7C15L;
        hash = (hash ^ latitudeCell) * 0xBF58_476D_1CE4_E5B9L;
        hash = (hash ^ longitudeCell) * 0x94D0_49BB_1331_11EBL;
        hash = (hash ^ epochDay) * 0xBF58_476D_1CE4_E5B9L;
        return hash ^ (hash >>> 31);
    }

    private ZoneId zone() {
        return properties.zone();
    }

    private record CatalogItem(String name, String storeName, String categoryCode, long originalPrice) {
    }
}
