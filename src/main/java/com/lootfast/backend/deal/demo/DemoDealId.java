package com.lootfast.backend.deal.demo;

import java.util.Optional;
import java.util.UUID;

/**
 * Identity of a generated demo deal (or its demo store), packed into an RFC 9562 version-8 UUID.
 *
 * <p>Real deals and users get random version-4 UUIDs, so the version nibble alone separates demo
 * ids from real ones without touching the database. Because the id carries the grid cell, day and
 * slot it was generated from, the same demo deal can be regenerated from the id alone (for the
 * detail endpoint) instead of being stored.
 *
 * <pre>
 * most significant 64 bits : magic(32) | latitudeCell(16) | version=8(4) | kind(1) | slot(3) | 0(8)
 * least significant 64 bits: variant=10(2) | 0(6) | longitudeCell(32) | epochDay(24)
 * </pre>
 */
public record DemoDealId(Kind kind, int latitudeCell, int longitudeCell, long epochDay, int slot) {

    public enum Kind {
        DEAL,
        STORE
    }

    /** ASCII "LFDE" (LootFast DEmo). */
    private static final long MAGIC = 0x4C46_4445L;
    private static final int VERSION = 8;
    private static final long VARIANT = 0b10L;
    private static final long MAX_EPOCH_DAY = 0xFF_FFFFL;

    public DemoDealId {
        if (kind == null) {
            throw new IllegalArgumentException("kind is required");
        }
        if (latitudeCell < Short.MIN_VALUE || latitudeCell > Short.MAX_VALUE) {
            throw new IllegalArgumentException("latitudeCell is out of range");
        }
        if (epochDay < 0 || epochDay > MAX_EPOCH_DAY) {
            throw new IllegalArgumentException("epochDay is out of range");
        }
        if (slot < 0 || slot > 7) {
            throw new IllegalArgumentException("slot is out of range");
        }
    }

    public DemoDealId withKind(Kind newKind) {
        return new DemoDealId(newKind, latitudeCell, longitudeCell, epochDay, slot);
    }

    public UUID toUuid() {
        long mostSignificant = (MAGIC << 32)
                | ((latitudeCell & 0xFFFFL) << 16)
                | ((long) VERSION << 12)
                | ((long) kind.ordinal() << 11)
                | ((long) slot << 8);
        long leastSignificant = (VARIANT << 62)
                | ((longitudeCell & 0xFFFF_FFFFL) << 24)
                | epochDay;
        return new UUID(mostSignificant, leastSignificant);
    }

    /** Returns the decoded id, or empty when the UUID is not a LootFast demo id. */
    public static Optional<DemoDealId> parse(UUID uuid) {
        if (uuid == null || uuid.version() != VERSION || uuid.variant() != VARIANT) {
            return Optional.empty();
        }
        long mostSignificant = uuid.getMostSignificantBits();
        long leastSignificant = uuid.getLeastSignificantBits();
        boolean reservedBitsClear = (mostSignificant & 0xFFL) == 0
                && ((leastSignificant >>> 56) & 0x3FL) == 0;
        if ((mostSignificant >>> 32) != MAGIC || !reservedBitsClear) {
            return Optional.empty();
        }
        Kind kind = ((mostSignificant >>> 11) & 1L) == 0 ? Kind.DEAL : Kind.STORE;
        int slot = (int) ((mostSignificant >>> 8) & 0x7L);
        int latitudeCell = (short) ((mostSignificant >>> 16) & 0xFFFFL);
        int longitudeCell = (int) ((leastSignificant >>> 24) & 0xFFFF_FFFFL);
        long epochDay = leastSignificant & MAX_EPOCH_DAY;
        return Optional.of(new DemoDealId(kind, latitudeCell, longitudeCell, epochDay, slot));
    }
}
