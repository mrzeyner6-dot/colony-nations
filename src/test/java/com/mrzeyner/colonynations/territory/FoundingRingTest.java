package com.mrzeyner.colonynations.territory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    private static ChunkRef chunk(int x, int z) {
        return new ChunkRef("minecraft:overworld", x, z);
    }
}
