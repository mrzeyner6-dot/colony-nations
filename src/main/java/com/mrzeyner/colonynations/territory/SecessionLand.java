package com.mrzeyner.colonynations.territory;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import com.mrzeyner.colonynations.nation.CityRef;

/**
 * State land that leaves with a city: every chunk whose nearest city is that one.
 */
public final class SecessionLand {
    private SecessionLand() {
    }

    public static int chebyshev(ChunkRef claim, ChunkRef land) {
        if (claim == null || land == null || !claim.dimension().equals(land.dimension())) {
            return Integer.MAX_VALUE;
        }
        return Math.max(Math.abs(claim.x() - land.x()), Math.abs(claim.z() - land.z()));
    }

    /**
     * Smallest Chebyshev distance from any claim chunk to any land chunk in the same dimension.
     * Empty or disjoint sets return {@link Integer#MAX_VALUE}.
     */
    public static int nearest(Set<ChunkRef> claim, Set<ChunkRef> land) {
        int best = Integer.MAX_VALUE;
        if (claim == null || land == null) {
            return best;
        }
        for (ChunkRef origin : claim) {
            for (ChunkRef chunk : land) {
                int distance = chebyshev(origin, chunk);
                if (distance < best) {
                    best = distance;
                }
            }
        }
        return best;
    }

    public static Set<ChunkRef> following(CityRef city, Collection<ChunkRef> state, Collection<NearestCity.Anchor> cities) {
        Set<ChunkRef> moved = new HashSet<>();
        if (city == null || state == null || cities == null) {
            return moved;
        }
        for (ChunkRef chunk : state) {
            if (city.equals(NearestCity.find(chunk, cities).orElse(null))) {
                moved.add(chunk);
            }
        }
        return Set.copyOf(moved);
    }
}
