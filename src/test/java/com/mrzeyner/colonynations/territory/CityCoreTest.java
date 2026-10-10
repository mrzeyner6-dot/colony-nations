package com.mrzeyner.colonynations.territory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class CityCoreTest {
    @Test
    void ringAroundEachCityIsCoreAndTheClaimItselfIsNot() {
        Set<ChunkRef> first = Set.of(chunk("minecraft:overworld", 0, 0), chunk("minecraft:overworld", 1, 0));
        Set<ChunkRef> second = Set.of(chunk("minecraft:the_nether", 0, 0));
        Set<ChunkRef> state = Set.of(
                chunk("minecraft:overworld", 0, 0),
                chunk("minecraft:overworld", 3, 0),
                chunk("minecraft:overworld", 4, 0),
                chunk("minecraft:the_nether", 2, 0),
                chunk("minecraft:the_nether", 3, 0));

        Set<ChunkRef> core = CityCore.among(state, List.of(first, second), 2);

        assertTrue(core.contains(chunk("minecraft:overworld", 3, 0)));
        assertTrue(core.contains(chunk("minecraft:the_nether", 2, 0)));
        assertFalse(core.contains(chunk("minecraft:overworld", 0, 0)));
        assertFalse(core.contains(chunk("minecraft:overworld", 4, 0)));
        assertFalse(core.contains(chunk("minecraft:the_nether", 3, 0)));
        assertEquals(2, core.size());
    }

    @Test
    void zeroWidthOrEmptyClaimsLeaveEverythingAnnexed() {
        Set<ChunkRef> state = Set.of(chunk("minecraft:overworld", 1, 0));
        Set<ChunkRef> claim = Set.of(chunk("minecraft:overworld", 0, 0));

        assertTrue(CityCore.among(state, List.of(claim), 0).isEmpty());
        assertTrue(CityCore.among(state, List.of(), 2).isEmpty());
        assertTrue(CityCore.among(Set.of(), List.of(claim), 2).isEmpty());
    }

    private static ChunkRef chunk(String dimension, int x, int z) {
        return new ChunkRef(dimension, x, z);
    }
}
