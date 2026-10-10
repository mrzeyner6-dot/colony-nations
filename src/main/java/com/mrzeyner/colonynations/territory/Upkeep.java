package com.mrzeyner.colonynations.territory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;

/**
 * Daily state-land upkeep. City land is not charged.
 * When the treasury cannot cover the bill, the furthest removable chunks are dropped without cutting the rest off from cities.
 */
public final class Upkeep {
    /**
     * @param dimension dimension id of the city center
     */
    public record CityPoint(String dimension, int x, int z) {
    }

    public record Charge(long paid, boolean shortfall) {
    }

    /**
     * Whether this check should charge, and the day number to store afterwards.
     * A missing last day is the first launch: record the day and do not charge.
     * A jump of several days charges once and stores the current day, not each skipped day.
     */
    public record Due(boolean charge, long recordedDay) {
    }

    public static final long TICKS_PER_DAY = 24000L;

    private Upkeep() {
    }

    public static Due due(OptionalLong lastDay, long dayTime) {
        long day = dayTime / TICKS_PER_DAY;
        if (lastDay.isEmpty()) {
            return new Due(false, day);
        }
        if (day > lastDay.getAsLong()) {
            return new Due(true, day);
        }
        return new Due(false, lastDay.getAsLong());
    }

    public static Charge charge(long treasury, long cost) {
        long available = Math.max(0L, treasury);
        long due = Math.max(0L, cost);
        long paid = Math.min(available, due);
        return new Charge(paid, paid < due);
    }

    /**
     * Furthest chunks first. Equal distance prefers the greater x, then greater z, then the dimension string.
     * A chunk is skipped when removing it would leave other state land unable to reach a city.
     */
    public static List<ChunkRef> furthestRemovable(Set<ChunkRef> state, Set<ChunkRef> cities, List<CityPoint> centers, int limit) {
        if (limit <= 0 || state.isEmpty()) {
            return List.of();
        }
        List<ChunkRef> released = new ArrayList<>();
        Set<ChunkRef> current = new HashSet<>(state);
        for (int i = 0; i < limit && !current.isEmpty(); i++) {
            ChunkRef pick = null;
            long bestDistance = -1L;
            for (ChunkRef chunk : current) {
                if (!TerritoryLink.staysConnected(current, cities, chunk)) {
                    continue;
                }
                long distance = distanceToNearest(chunk, centers);
                if (pick == null || distance > bestDistance || (distance == bestDistance && later(chunk, pick))) {
                    pick = chunk;
                    bestDistance = distance;
                }
            }
            if (pick == null) {
                break;
            }
            current.remove(pick);
            released.add(pick);
        }
        return List.copyOf(released);
    }

    private static boolean later(ChunkRef candidate, ChunkRef current) {
        if (candidate.x() != current.x()) {
            return candidate.x() > current.x();
        }
        if (candidate.z() != current.z()) {
            return candidate.z() > current.z();
        }
        return candidate.dimension().compareTo(current.dimension()) > 0;
    }

    private static long distanceToNearest(ChunkRef chunk, List<CityPoint> centers) {
        long best = Long.MAX_VALUE;
        for (CityPoint center : centers) {
            if (!chunk.dimension().equals(center.dimension())) {
                continue;
            }
            long dx = (long) chunk.x() - center.x();
            long dz = (long) chunk.z() - center.z();
            long distance = dx * dx + dz * dz;
            if (distance < best) {
                best = distance;
            }
        }
        return best;
    }
}
