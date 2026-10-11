package com.mrzeyner.colonynations.territory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class FoundingRingTest {
    @Test
    void widthOneIsTheEightNeighbors() {
        Set<ChunkRef> ring = FoundingRing.around(Set.of(chunk(0, 0)), 1);

        assertEquals(8, ring.size());
        assertTrue(ring.contains(chunk(1, 1)));
        assertTrue(ring.contains(chunk(0, 1)));
        assertFalse(ring.contains(chunk(0, 0)));
        assertFalse(ring.contains(chunk(2, 0)));
    }

    @Test
    void widthTwoReachesTwoChunksOutAndSkipsTheClaim() {
        Set<ChunkRef> claim = Set.of(chunk(0, 0), chunk(1, 0));
        Set<ChunkRef> ring = FoundingRing.around(claim, 2);

        assertTrue(ring.contains(chunk(2, 2)));
        assertTrue(ring.contains(chunk(-2, 0)));
        assertTrue(ring.contains(chunk(3, 0)));
        assertFalse(ring.contains(chunk(0, 0)));
        assertFalse(ring.contains(chunk(1, 0)));
        assertFalse(ring.contains(chunk(4, 0)));
    }

    @Test
    void emptyClaimOrZeroWidthAddsNothing() {
        assertTrue(FoundingRing.around(Set.of(), 2).isEmpty());
        assertTrue(FoundingRing.around(Set.of(chunk(0, 0)), 0).isEmpty());
    }

    @Test
    void boughtCityBetweenTwoNationsFillsOnlyWilderness() {
        Set<ChunkRef> claim = Set.of(chunk(0, 0));
        int width = 2;
        Set<ChunkRef> ring = FoundingRing.around(claim, width);
        Set<ChunkRef> nationA = Set.of(chunk(-2, -1), chunk(-2, 0), chunk(-2, 1), chunk(-5, 0));
        Set<ChunkRef> nationB = Set.of(chunk(2, -1), chunk(2, 0), chunk(2, 1));
        Set<ChunkRef> ownState = Set.of(chunk(1, -1));
        Set<ChunkRef> otherFreeCity = Set.of(chunk(-1, 2), chunk(0, 2), chunk(1, 2), chunk(0, 4));
        Set<ChunkRef> occupied = new HashSet<>();
        occupied.addAll(nationA);
        occupied.addAll(nationB);
        occupied.addAll(ownState);
        occupied.addAll(otherFreeCity);

        Set<ChunkRef> annexed = FoundingRing.unowned(ring, occupied);

        assertEquals(24, ring.size());
        assertEquals(14, annexed.size());
        assertEquals(annexed, CityCore.among(annexed, List.of(claim), width));
        assertTrue(annexed.contains(chunk(0, 1)));
        assertTrue(annexed.contains(chunk(0, -2)));
        assertFalse(annexed.contains(chunk(0, 0)));
        assertFalse(annexed.contains(chunk(-2, 0)));
        assertFalse(annexed.contains(chunk(2, 0)));
        assertFalse(annexed.contains(chunk(1, -1)));
        assertFalse(annexed.contains(chunk(0, 2)));
        assertFalse(annexed.contains(chunk(-5, 0)));
        assertFalse(annexed.contains(chunk(0, 4)));
        for (ChunkRef chunk : annexed) {
            assertTrue(ring.contains(chunk));
            assertFalse(occupied.contains(chunk));
        }
    }

    @Test
    void emptyOccupationKeepsTheWholeRing() {
        Set<ChunkRef> ring = FoundingRing.around(Set.of(chunk(0, 0)), 1);

        assertEquals(ring, FoundingRing.unowned(ring, Set.of()));
        assertEquals(ring, FoundingRing.unowned(ring, null));
        assertTrue(FoundingRing.unowned(Set.of(), Set.of(chunk(1, 0))).isEmpty());
        assertTrue(FoundingRing.unowned(null, ring).isEmpty());
    }

    private static ChunkRef chunk(int x, int z) {
        return new ChunkRef("minecraft:overworld", x, z);
    }
}
