package com.mrzeyner.colonynations.territory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.OptionalLong;
import java.util.Set;

import org.junit.jupiter.api.Test;

class UpkeepTest {
    @Test
    void dawnChargesOnceAndStoresThatDay() {
        Upkeep.Due due = Upkeep.due(OptionalLong.of(4L), 5L * Upkeep.TICKS_PER_DAY);

        assertTrue(due.charge());
        assertEquals(5L, due.recordedDay());
    }

    @Test
    void frozenDayDoesNotChargeAgain() {
        long dayTime = 3L * Upkeep.TICKS_PER_DAY + 1000L;

        Upkeep.Due due = Upkeep.due(OptionalLong.of(3L), dayTime);

        assertFalse(due.charge());
        assertEquals(3L, due.recordedDay());
    }

    @Test
    void skippedDaysChargeOnce() {
        Upkeep.Due due = Upkeep.due(OptionalLong.of(1L), 5L * Upkeep.TICKS_PER_DAY);

        assertTrue(due.charge());
        assertEquals(5L, due.recordedDay());
    }

    @Test
    void firstLaunchRecordsTheDayWithoutCharging() {
        Upkeep.Due due = Upkeep.due(OptionalLong.empty(), 2L * Upkeep.TICKS_PER_DAY + 50L);

        assertFalse(due.charge());
        assertEquals(2L, due.recordedDay());
    }

    @Test
    void fullTreasuryPaysTheBill() {
        Upkeep.Charge charge = Upkeep.charge(100L, 6L);

        assertEquals(6L, charge.paid());
        assertFalse(charge.shortfall());
    }

    @Test
    void shortTreasuryPaysEverythingItHas() {
        Upkeep.Charge charge = Upkeep.charge(5L, 20L);

        assertEquals(5L, charge.paid());
        assertTrue(charge.shortfall());
    }

    @Test
    void shortfallReleasesTheFarLeafAndKeepsTheBridge() {
        Set<ChunkRef> cities = Set.of(chunk(0, 0));
        Set<ChunkRef> state = Set.of(chunk(1, 0), chunk(2, 0), chunk(1, 3));
        List<Upkeep.CityPoint> centers = List.of(new Upkeep.CityPoint("minecraft:overworld", 0, 0));

        List<ChunkRef> released = Upkeep.furthestRemovable(state, cities, centers, 1);

        assertEquals(List.of(chunk(1, 3)), released);
    }

    @Test
    void bridgeIsKeptWhileLandIsBehindIt() {
        Set<ChunkRef> cities = Set.of(chunk(0, 0));
        Set<ChunkRef> state = Set.of(chunk(1, 0), chunk(2, 0));
        List<Upkeep.CityPoint> centers = List.of(new Upkeep.CityPoint("minecraft:overworld", 0, 0));

        assertEquals(List.of(chunk(2, 0)), Upkeep.furthestRemovable(state, cities, centers, 1));
        assertEquals(List.of(chunk(2, 0), chunk(1, 0)), Upkeep.furthestRemovable(state, cities, centers, 4));
    }

    @Test
    void shortfallDoesNotReleaseTheCore() {
        Set<ChunkRef> cities = Set.of(chunk(0, 0));
        Set<ChunkRef> state = Set.of(chunk(1, 0), chunk(2, 0), chunk(3, 0));
        Set<ChunkRef> core = Set.of(chunk(1, 0), chunk(2, 0));
        List<Upkeep.CityPoint> centers = List.of(new Upkeep.CityPoint("minecraft:overworld", 0, 0));

        assertEquals(List.of(chunk(3, 0)), Upkeep.furthestRemovable(state, core, cities, centers, 4));
    }

    private static ChunkRef chunk(int x, int z) {
        return new ChunkRef("minecraft:overworld", x, z);
    }
}
