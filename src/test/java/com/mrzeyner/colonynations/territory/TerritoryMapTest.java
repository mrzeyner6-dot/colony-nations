package com.mrzeyner.colonynations.territory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class TerritoryMapTest {
    private final UUID rome = UUID.randomUUID();
    private final UUID gaul = UUID.randomUUID();

    @Test
    void claimReleaseAndCount() {
        TerritoryMap map = new TerritoryMap();
        ChunkRef home = chunk("minecraft:overworld", 3, -1);

        map.claim(home, rome);
        map.claim(home, rome);

        assertEquals(rome, map.owner(home).orElseThrow());
        assertEquals(1, map.count(rome));
        assertEquals(1, map.chunks(rome).size());
        assertTrue(map.chunks(gaul).isEmpty());

        map.release(home);
        map.release(home);

        assertTrue(map.owner(home).isEmpty());
        assertEquals(0, map.count(rome));
    }

    @Test
    void secondNationCannotClaimTheSameChunk() {
        TerritoryMap map = new TerritoryMap();
        ChunkRef home = chunk("minecraft:overworld", 0, 0);
        map.claim(home, rome);

        assertThrows(IllegalStateException.class, () -> map.claim(home, gaul));
        assertEquals(rome, map.owner(home).orElseThrow());
    }

    @Test
    void orthogonalNeighborsAreAdjacentAndDiagonalsAreNot() {
        TerritoryMap map = new TerritoryMap();
        ChunkRef origin = chunk("minecraft:overworld", 0, 0);
        map.claim(origin, rome);

        assertTrue(map.isAdjacentToNation(chunk("minecraft:overworld", 1, 0), rome));
        assertTrue(map.isAdjacentToNation(chunk("minecraft:overworld", -1, 0), rome));
        assertTrue(map.isAdjacentToNation(chunk("minecraft:overworld", 0, 1), rome));
        assertTrue(map.isAdjacentToNation(chunk("minecraft:overworld", 0, -1), rome));
        assertFalse(map.isAdjacentToNation(chunk("minecraft:overworld", 1, 1), rome));
        assertFalse(map.isAdjacentToNation(origin, rome));
        assertFalse(map.isAdjacentToNation(chunk("minecraft:overworld", 1, 0), gaul));
        assertFalse(map.isAdjacentToNation(chunk("minecraft:the_nether", 1, 0), rome));
    }

    @Test
    void releaseAllDropsOnlyThatNation() {
        TerritoryMap map = new TerritoryMap();
        ChunkRef romeChunk = chunk("minecraft:overworld", 2, 2);
        ChunkRef gaulChunk = chunk("minecraft:the_nether", 2, 2);
        map.claim(romeChunk, rome);
        map.claim(gaulChunk, gaul);

        map.releaseAll(rome);
        map.releaseAll(rome);

        assertTrue(map.owner(romeChunk).isEmpty());
        assertEquals(gaul, map.owner(gaulChunk).orElseThrow());
        assertEquals(0, map.count(rome));
        assertEquals(1, map.count(gaul));
    }

    private static ChunkRef chunk(String dimension, int x, int z) {
        return new ChunkRef(dimension, x, z);
    }
}
