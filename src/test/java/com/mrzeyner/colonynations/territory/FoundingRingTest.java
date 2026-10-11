package com.mrzeyner.colonynations.territory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class FoundingRingTest {
    private static final String OVERWORLD = "minecraft:overworld";

    @Test
    void ringIsChessboardNeighbourhoodOutsideTheClaim() {
        Set<ChunkRef> claim = Set.of(chunk(0, 0), chunk(1, 0));
        Set<ChunkRef> ring = FoundingRing.around(claim, 1);

        assertTrue(ring.contains(chunk(-1, 0)));
        assertTrue(ring.contains(chunk(0, 1)));
        assertTrue(ring.contains(chunk(2, 0)));
        assertFalse(ring.contains(chunk(0, 0)));
        assertFalse(ring.contains(chunk(1, 0)));
        assertFalse(ring.contains(chunk(3, 0)));
        assertEquals(10, ring.size());
    }

    @Test
    void widthZeroOrEmptyClaimAddsNothing() {
        assertTrue(FoundingRing.around(Set.of(chunk(0, 0)), 0).isEmpty());
        assertTrue(FoundingRing.around(Set.of(), 2).isEmpty());
        assertEquals(24, FoundingRing.around(Set.of(chunk(0, 0)), 2).size());
    }

    @Test
    void grantReleasesClaimAndSkipsForeignOrCityChunks() {
        TerritoryMap map = new TerritoryMap();
        UUID rome = UUID.randomUUID();
        UUID gaul = UUID.randomUUID();
        ChunkRef inside = chunk(0, 0);
        ChunkRef foreignRing = chunk(1, 0);
        ChunkRef cityRing = chunk(0, 1);
        ChunkRef free = chunk(-1, 0);
        map.claim(inside, gaul);
        map.claim(foreignRing, gaul);
        map.claim(chunk(4, 4), rome);

        FoundingRing.grant(map, rome, Set.of(inside), Set.of(cityRing), 1);

        assertTrue(map.owner(inside).isEmpty());
        assertEquals(gaul, map.owner(foreignRing).orElseThrow());
        assertTrue(map.owner(cityRing).isEmpty());
        assertEquals(rome, map.owner(free).orElseThrow());
        assertEquals(rome, map.owner(chunk(4, 4)).orElseThrow());
        assertFalse(map.chunks(rome).contains(inside));
    }

    private static ChunkRef chunk(int x, int z) {
        return new ChunkRef(OVERWORLD, x, z);
    }
}
