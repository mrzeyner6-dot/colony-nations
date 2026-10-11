package com.mrzeyner.colonynations.territory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.mrzeyner.colonynations.nation.CityRef;

class UpkeepTest {
    private static final String OVERWORLD = "minecraft:overworld";

    @Test
    void fullTreasuryPaysAndKeepsTheLand() {
        Set<ChunkRef> state = Set.of(chunk(1, 0), chunk(2, 0));
        Upkeep.Settlement settlement = Upkeep.settle(100L, 2, 4, state, Set.of(chunk(0, 0)), centers());

        assertEquals(4L, settlement.paid());
        assertTrue(settlement.released().isEmpty());
    }

    @Test
    void shortfallTakesWhatIsLeftAndReleasesTheFarthest() {
        Set<ChunkRef> state = Set.of(chunk(1, 0), chunk(2, 0), chunk(3, 0));
        Upkeep.Settlement settlement = Upkeep.settle(5L, 2, 2, state, Set.of(chunk(0, 0)), centers());

        assertEquals(5L, settlement.paid());
        assertEquals(List.of(chunk(3, 0), chunk(2, 0)), settlement.released());
    }

    @Test
    void emptyTreasuryStillReleasesAndABridgeIsKeptUntilTheFarEndIsGone() {
        Set<ChunkRef> state = Set.of(chunk(1, 0), chunk(2, 0));
        Upkeep.Settlement one = Upkeep.settle(0L, 2, 1, state, Set.of(chunk(0, 0)), centers());

        assertEquals(0L, one.paid());
        assertEquals(List.of(chunk(2, 0)), one.released());

        Upkeep.Settlement both = Upkeep.settle(0L, 2, 4, state, Set.of(chunk(0, 0)), centers());
        assertEquals(List.of(chunk(2, 0), chunk(1, 0)), both.released());
    }

    @Test
    void equalDistanceReleasesTheHigherCoordinatesFirst() {
        Set<ChunkRef> state = Set.of(chunk(1, 0), chunk(-1, 0), chunk(0, 1));
        List<ChunkRef> released = Upkeep.releaseFarthest(state, Set.of(chunk(0, 0)), centers(), 2);

        assertEquals(List.of(chunk(1, 0), chunk(0, 1)), released);
    }

    @Test
    void nothingDueReleasesNothing() {
        Upkeep.Settlement settlement = Upkeep.settle(0L, 2, 4, Set.of(), Set.of(chunk(0, 0)), centers());
        assertEquals(0L, settlement.paid());
        assertTrue(settlement.released().isEmpty());
        assertTrue(Upkeep.settle(10L, 0, 4, Set.of(chunk(1, 0)), Set.of(chunk(0, 0)), centers()).released().isEmpty());
    }

    private static List<CityCenter> centers() {
        return List.of(new CityCenter(new CityRef(OVERWORLD, 1), chunk(0, 0)));
    }

    private static ChunkRef chunk(int x, int z) {
        return new ChunkRef(OVERWORLD, x, z);
    }
}
