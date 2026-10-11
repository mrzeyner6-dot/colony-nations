package com.mrzeyner.colonynations.territory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.mrzeyner.colonynations.nation.CityRef;

/**
 * Daily upkeep for state land. City land is not charged.
 * A shortfall takes the whole treasury and releases the farthest chunks that keep the rest linked to cities.
 */
public final class Upkeep {
    private Upkeep() {
    }

    public record Settlement(long paid, List<ChunkRef> released) {
    }

    public static Settlement settle(
            long treasury,
            int perChunk,
            int releasePerDay,
            Set<ChunkRef> state,
            Set<ChunkRef> cities,
            Collection<CityCenter> centers) {
        long due = (long) state.size() * perChunk;
        if (due <= 0) {
            return new Settlement(0L, List.of());
        }
        if (treasury >= due) {
            return new Settlement(due, List.of());
        }
        long paid = Math.max(0L, treasury);
        if (releasePerDay <= 0) {
            return new Settlement(paid, List.of());
        }
        return new Settlement(paid, releaseFarthest(state, cities, centers, releasePerDay));
    }

    /**
     * Farthest from the nearest city first. Equal distances release the higher x, then the higher z, then the
     * dimension string later in alphabet order. A chunk is skipped when releasing it would cut the rest off from cities.
     */
    public static List<ChunkRef> releaseFarthest(
            Set<ChunkRef> state,
            Set<ChunkRef> cities,
            Collection<CityCenter> centers,
            int count) {
        if (count <= 0 || state.isEmpty()) {
            return List.of();
        }
        List<ChunkRef> order = new ArrayList<>(state);
        order.sort(farthestFirst(centers));
        Set<ChunkRef> remaining = new HashSet<>(state);
        List<ChunkRef> released = new ArrayList<>();
        for (ChunkRef chunk : order) {
            if (released.size() >= count) {
                break;
            }
            if (!TerritoryLinks.staysConnected(remaining, cities, chunk)) {
                continue;
            }
            remaining.remove(chunk);
            released.add(chunk);
        }
        return List.copyOf(released);
    }

    private static Comparator<ChunkRef> farthestFirst(Collection<CityCenter> centers) {
        return Comparator.comparingInt((ChunkRef chunk) -> distance(chunk, centers))
                .reversed()
                .thenComparing(ChunkRef::x, Comparator.reverseOrder())
                .thenComparing(ChunkRef::z, Comparator.reverseOrder())
                .thenComparing(ChunkRef::dimension, Comparator.reverseOrder());
    }

    private static int distance(ChunkRef chunk, Collection<CityCenter> centers) {
        CityRef nearest = NearestCity.find(chunk, centers).orElse(null);
        if (nearest == null) {
            return Integer.MAX_VALUE;
        }
        for (CityCenter center : centers) {
            if (center.city().equals(nearest)) {
                return NearestCity.chunkDistance(chunk, center.center());
            }
        }
        return Integer.MAX_VALUE;
    }
}
