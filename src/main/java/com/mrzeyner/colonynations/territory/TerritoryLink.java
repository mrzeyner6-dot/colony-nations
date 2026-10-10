package com.mrzeyner.colonynations.territory;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Orthogonal links between chunks. State land may be released only while every remaining chunk can still reach a city.
 */
public final class TerritoryLink {
    private static final int[][] NEIGHBORS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private TerritoryLink() {
    }

    public static boolean touches(ChunkRef chunk, Set<ChunkRef> owned) {
        for (int[] step : NEIGHBORS) {
            if (owned.contains(chunk.offset(step[0], step[1]))) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param cities chunks of the nation's city land; they anchor state land but are not themselves state land
     * @return true when removing {@code removing} leaves every other state chunk connected to some city
     */
    public static boolean staysConnected(Set<ChunkRef> state, Set<ChunkRef> cities, ChunkRef removing) {
        Set<ChunkRef> remaining = new HashSet<>(state);
        remaining.remove(removing);
        if (remaining.isEmpty()) {
            return true;
        }
        ArrayDeque<ChunkRef> queue = new ArrayDeque<>();
        Set<ChunkRef> reached = new HashSet<>();
        for (ChunkRef chunk : remaining) {
            if (touches(chunk, cities)) {
                queue.add(chunk);
                reached.add(chunk);
            }
        }
        while (!queue.isEmpty()) {
            ChunkRef current = queue.removeFirst();
            for (int[] step : NEIGHBORS) {
                ChunkRef neighbor = current.offset(step[0], step[1]);
                if (remaining.contains(neighbor) && reached.add(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }
        return reached.size() == remaining.size();
    }
}
