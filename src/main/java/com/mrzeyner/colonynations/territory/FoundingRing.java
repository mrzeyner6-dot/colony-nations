package com.mrzeyner.colonynations.territory;

import java.util.HashSet;
import java.util.Set;

/**
 * Free ring annexed around a colony claim when the colony is founded or a free city is bought.
 * Width is Chebyshev distance in chunks: a width of 1 is the eight surrounding chunks.
 * Only unowned chunks of that ring are taken; existing borders stay where they are.
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

    /**
     * Ring chunks that nobody holds. {@code occupied} is state land of any nation, city land,
     * and other free-city claims. Chunks outside {@code ring} are never returned.
     */
    public static Set<ChunkRef> unowned(Set<ChunkRef> ring, Set<ChunkRef> occupied) {
        if (ring == null || ring.isEmpty()) {
            return Set.of();
        }
        if (occupied == null || occupied.isEmpty()) {
            return Set.copyOf(ring);
        }
        Set<ChunkRef> free = new HashSet<>();
        for (ChunkRef chunk : ring) {
            if (!occupied.contains(chunk)) {
                free.add(chunk);
            }
        }
        return Set.copyOf(free);
    }
}
