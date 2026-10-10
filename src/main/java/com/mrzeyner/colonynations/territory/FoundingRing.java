package com.mrzeyner.colonynations.territory;

import java.util.HashSet;
import java.util.Set;

/**
 * Free ring annexed around a colony claim when the colony joins a nation.
 * Width is Chebyshev distance in chunks: a width of 1 is the eight surrounding chunks.
 */
public final class FoundingRing {
    private FoundingRing() {
    }

    public static Set<ChunkRef> around(Set<ChunkRef> claim, int width) {
        if (claim == null || claim.isEmpty() || width <= 0) {
            return Set.of();
        }
        Set<ChunkRef> ring = new HashSet<>();
        for (ChunkRef chunk : claim) {
            for (int dx = -width; dx <= width; dx++) {
                for (int dz = -width; dz <= width; dz++) {
                    int distance = Math.max(Math.abs(dx), Math.abs(dz));
                    if (distance < 1 || distance > width) {
                        continue;
                    }
                    ChunkRef neighbor = chunk.offset(dx, dz);
                    if (!claim.contains(neighbor)) {
                        ring.add(neighbor);
                    }
                }
            }
        }
        return Set.copyOf(ring);
    }
}
