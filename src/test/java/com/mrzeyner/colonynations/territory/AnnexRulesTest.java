package com.mrzeyner.colonynations.territory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

class AnnexRulesTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";

    @Test
    void priceAndAdministrativeLimit() {
        assertEquals(50L, AnnexCost.price(0, 50, 10));
        assertEquals(90L, AnnexCost.price(4, 50, 10));
        assertThrows(IllegalArgumentException.class, () -> AnnexCost.price(-1, 50, 10));
        assertEquals(85L, AdminLimit.chunks(30, 20, 2, 5, 3));
    }

    @Test
    void adjacencyIsOrthogonalNationLand() {
        Set<ChunkRef> land = Set.of(chunk(OVERWORLD, 1, 0), chunk(NETHER, 0, 1));
        assertTrue(NationReach.touches(chunk(OVERWORLD, 0, 0), land));
        assertFalse(NationReach.touches(chunk(OVERWORLD, 0, 1), land));
        assertFalse(NationReach.touches(chunk(OVERWORLD, 2, 2), land));
        assertTrue(NationReach.touches(chunk(NETHER, 0, 0), land));
        assertFalse(NationReach.touches(chunk(OVERWORLD, 1, 0), land));
    }

    @Test
    void releasingABridgeCutsTheLinkToTheCity() {
        Set<ChunkRef> state = Set.of(chunk(OVERWORLD, 1, 0), chunk(OVERWORLD, 2, 0));
        Set<ChunkRef> cities = Set.of(chunk(OVERWORLD, 0, 0));

        assertTrue(TerritoryLinks.staysConnected(state, cities, chunk(OVERWORLD, 2, 0)));
        assertFalse(TerritoryLinks.staysConnected(state, cities, chunk(OVERWORLD, 1, 0)));
        assertTrue(TerritoryLinks.staysConnected(state, cities, null));
        assertFalse(TerritoryLinks.staysConnected(
                Set.of(chunk(OVERWORLD, 1, 0), chunk(OVERWORLD, 3, 0)),
                cities,
                null));
        assertTrue(TerritoryLinks.staysConnected(Set.of(chunk(OVERWORLD, 1, 0)), cities, chunk(OVERWORLD, 1, 0)));
    }

    @Test
    void diagonalAndOtherDimensionDoNotConnect() {
        Set<ChunkRef> cities = Set.of(chunk(OVERWORLD, 0, 0));
        assertFalse(TerritoryLinks.staysConnected(Set.of(chunk(OVERWORLD, 1, 1)), cities, null));
        assertFalse(TerritoryLinks.staysConnected(Set.of(chunk(NETHER, 1, 0)), cities, null));
        assertTrue(TerritoryLinks.staysConnected(
                Set.of(chunk(OVERWORLD, 1, 0), chunk(NETHER, 1, 0)),
                Set.of(chunk(OVERWORLD, 0, 0), chunk(NETHER, 0, 0)),
                null));
    }

    private static ChunkRef chunk(String dimension, int x, int z) {
        return new ChunkRef(dimension, x, z);
    }
}
