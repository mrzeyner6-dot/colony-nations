package com.mrzeyner.colonynations.territory;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * State land within the founding ring of any city claim. The claim itself is city land, not core.
 * Core chunks stay state land, but they do not count toward the annex limit and do not pay upkeep.
 */
public final class CityCore {
    private CityCore() {
    }

    /**
     * @param state state-land chunks to classify
     * @param cityClaims claim chunks of each city; chunks from every set are considered
     * @param width Chebyshev ring width, the same unit as {@link FoundingRing}
     * @return the subset of {@code state} that lies in a ring and is not itself a claim chunk
     */
    public static Set<ChunkRef> among(Set<ChunkRef> state, Collection<? extends Collection<ChunkRef>> cityClaims, int width) {
        if (width <= 0 || state.isEmpty() || cityClaims == null || cityClaims.isEmpty()) {
            return Set.of();
        }
        Set<ChunkRef> claims = new HashSet<>();
        for (Collection<ChunkRef> claim : cityClaims) {
            if (claim != null) {
                claims.addAll(claim);
            }
        }
        if (claims.isEmpty()) {
            return Set.of();
        }
        Set<ChunkRef> core = new HashSet<>();
        for (ChunkRef chunk : state) {
            if (claims.contains(chunk)) {
                continue;
            }
            if (inRing(chunk, claims, width)) {
                core.add(chunk);
            }
        }
        return Set.copyOf(core);
    }

    private static boolean inRing(ChunkRef chunk, Set<ChunkRef> claims, int width) {
        for (ChunkRef claim : claims) {
            if (!chunk.dimension().equals(claim.dimension())) {
                continue;
            }
            int distance = Math.max(Math.abs(chunk.x() - claim.x()), Math.abs(chunk.z() - claim.z()));
            if (distance >= 1 && distance <= width) {
                return true;
            }
        }
        return false;
    }
}
