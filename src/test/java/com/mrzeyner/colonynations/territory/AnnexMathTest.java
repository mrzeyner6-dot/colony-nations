package com.mrzeyner.colonynations.territory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

class AnnexMathTest {
    @Test
    void priceAddsDistance() {
        assertEquals(50L, AnnexMath.price(50, 10, 0));
        assertEquals(80L, AnnexMath.price(50, 10, 3));
    }

    @Test
    void distanceMatchesTheNearestCityMetric() {
        assertEquals(3L, ChunkDistance.rounded(3, 0, 0, 0));
        assertEquals(5L, ChunkDistance.rounded(3, 4, 0, 0));
    }

    @Test
    void limitCountsCitiesAndTownHallLevels() {
        assertEquals(90, AnnexMath.limit(30, 20, 2, 5, 4));
    }

    @Test
    void adjacencyIsOrthogonal() {
        Set<ChunkRef> owned = Set.of(chunk(0, 0));
        assertTrue(TerritoryLink.touches(chunk(1, 0), owned));
        assertFalse(TerritoryLink.touches(chunk(1, 1), owned));
        assertFalse(TerritoryLink.touches(chunk(0, 0), owned));
    }

    @Test
    void releasingABridgeCutsOffLand() {
        Set<ChunkRef> cities = Set.of(chunk(0, 0));
        Set<ChunkRef> state = Set.of(chunk(1, 0), chunk(2, 0));

        assertFalse(TerritoryLink.staysConnected(state, cities, chunk(1, 0)));
        assertTrue(TerritoryLink.staysConnected(state, cities, chunk(2, 0)));
    }

    @Test
    void cityLandCanBridgeTwoStateChunks() {
        Set<ChunkRef> cities = Set.of(chunk(1, 0));
        Set<ChunkRef> state = Set.of(chunk(0, 0), chunk(2, 0));

        assertTrue(TerritoryLink.staysConnected(state, cities, chunk(0, 0)));
    }

    private static ChunkRef chunk(int x, int z) {
        return new ChunkRef("minecraft:overworld", x, z);
    }
}
