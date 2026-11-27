package com.lootfast.backend.deal.demo;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoDealIdTests {

    @Test
    void roundTripsEveryFieldIncludingNegativeCells() {
        DemoDealId[] ids = {
                new DemoDealId(DemoDealId.Kind.DEAL, 2_156, 21_340, 20_713, 0),
                new DemoDealId(DemoDealId.Kind.STORE, -6_754, -14_822, 20_714, 1),
                new DemoDealId(DemoDealId.Kind.DEAL, -18_000, -36_000, 0, 7),
                new DemoDealId(DemoDealId.Kind.STORE, 17_999, 35_999, 0xFF_FFFF, 3)
        };
        for (DemoDealId id : ids) {
            UUID uuid = id.toUuid();

            assertEquals(8, uuid.version());
            assertEquals(2, uuid.variant());
            assertEquals(id, DemoDealId.parse(uuid).orElseThrow());
            assertEquals(uuid, UUID.fromString(uuid.toString()));
        }
    }

    @Test
    void realRandomUuidsAreNeverDemoIds() {
        for (int i = 0; i < 1_000; i++) {
            assertTrue(DemoDealId.parse(UUID.randomUUID()).isEmpty());
        }
        assertTrue(DemoDealId.parse(null).isEmpty());
    }

    @Test
    void versionEightUuidsWithoutTheLootFastMarkerAreRejected() {
        UUID foreignVersionEight = new UUID(0x1234_5678_0000_8000L, 0x8000_0000_0000_0001L);

        assertEquals(8, foreignVersionEight.version());
        assertTrue(DemoDealId.parse(foreignVersionEight).isEmpty());
    }

    @Test
    void rejectsValuesThatDoNotFitTheLayout() {
        assertThrows(IllegalArgumentException.class,
                () -> new DemoDealId(DemoDealId.Kind.DEAL, 40_000, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new DemoDealId(DemoDealId.Kind.DEAL, 0, 0, -1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new DemoDealId(DemoDealId.Kind.DEAL, 0, 0, 0, 8));
    }
}
