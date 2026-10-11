package com.mrzeyner.colonynations.territory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class MapLayersTest {
    @Test
    void stateAndCityChunksDoNotShareAPolygon() {
        Set<ChunkRef> state = square(0, 0, 4, 4);
        Set<ChunkRef> claim = Set.of(chunk(2, 2));

        MapLayers.Split split = MapLayers.of(state, Map.of(7, claim));

        assertFalse(overlaps(split.state(), union(split.cities())));
        assertEquals(claim, split.cities().get(7));
        assertFalse(split.state().contains(chunk(2, 2)));

        Set<ChunkRef> everything = new HashSet<>(state);
        everything.addAll(claim);
        Set<ChunkRef> stateCover = MapLayers.covered(split.statePolygons(), everything);
        Set<ChunkRef> cityCover = MapLayers.covered(split.cityPolygons().get(7), everything);
        assertFalse(overlaps(stateCover, cityCover));
        assertTrue(cityCover.contains(chunk(2, 2)));
        assertFalse(stateCover.contains(chunk(2, 2)));
        assertEquals(1, split.statePolygons().get(0).holes().size());
    }

    @Test
    void claimThatGrewOntoStateLandLeavesTheStatePolygon() {
        Set<ChunkRef> formerState = square(0, 0, 2, 2);
        Set<ChunkRef> grownClaim = Set.of(chunk(2, 2), chunk(3, 3));

        MapLayers.Split split = MapLayers.of(formerState, Map.of(1, grownClaim));

        assertFalse(split.state().contains(chunk(2, 2)));
        assertEquals(Set.of(chunk(2, 2), chunk(3, 3)), split.cities().get(1));
        Set<ChunkRef> candidates = new HashSet<>(formerState);
        candidates.addAll(grownClaim);
        assertFalse(overlaps(
                MapLayers.covered(split.statePolygons(), candidates),
                MapLayers.covered(split.cityPolygons().get(1), candidates)));
        assertFalse(MapLayers.covered(split.statePolygons(), candidates).contains(chunk(2, 2)));
    }

    private static boolean overlaps(Set<ChunkRef> left, Set<ChunkRef> right) {
        for (ChunkRef chunk : left) {
            if (right.contains(chunk)) {
                return true;
            }
        }
        return false;
    }

    private static Set<ChunkRef> union(Map<Integer, Set<ChunkRef>> cities) {
        Set<ChunkRef> chunks = new HashSet<>();
        for (Set<ChunkRef> claim : cities.values()) {
            chunks.addAll(claim);
        }
        return chunks;
    }

    private static Set<ChunkRef> square(int x0, int z0, int x1, int z1) {
        Set<ChunkRef> chunks = new HashSet<>();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                chunks.add(chunk(x, z));
            }
        }
        return chunks;
    }

    private static ChunkRef chunk(int x, int z) {
        return new ChunkRef("minecraft:overworld", x, z);
    }
}
