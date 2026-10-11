package com.mrzeyner.colonynations.territory;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * State land must stay orthogonally connected to a city, walking through state or city chunks.
 */
public final class TerritoryLinks {
    private TerritoryLinks() {
    }

    /**
     * @param removing state chunk about to be released; null checks the map as it is
     * @return true when every remaining state chunk can reach a city chunk
     */
    public static boolean staysConnected(Set<ChunkRef> state, Set<ChunkRef> cities, ChunkRef removing) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(cities, "cities");
        Set<ChunkRef> remaining = new HashSet<>(state);
        if (removing != null) {
            remaining.remove(removing);
        }
        if (remaining.isEmpty()) {
            return true;
        }
        if (cities.isEmpty()) {
            return false;
        }
        Set<ChunkRef> walkable = new HashSet<>(remaining);
        walkable.addAll(cities);
        ArrayDeque<ChunkRef> queue = new ArrayDeque<>();
        Set<ChunkRef> seen = new HashSet<>();
        for (ChunkRef city : cities) {
            if (seen.add(city)) {
                queue.add(city);
            }
        }
        while (!queue.isEmpty()) {
            ChunkRef chunk = queue.removeFirst();
            offer(queue, seen, walkable, chunk.dimension(), chunk.x() + 1, chunk.z());
            offer(queue, seen, walkable, chunk.dimension(), chunk.x() - 1, chunk.z());
            offer(queue, seen, walkable, chunk.dimension(), chunk.x(), chunk.z() + 1);
            offer(queue, seen, walkable, chunk.dimension(), chunk.x(), chunk.z() - 1);
        }
        return seen.containsAll(remaining);
    }

    private static void offer(
            ArrayDeque<ChunkRef> queue,
            Set<ChunkRef> seen,
            Set<ChunkRef> walkable,
            String dimension,
            int x,
            int z) {
        ChunkRef next = new ChunkRef(dimension, x, z);
        if (walkable.contains(next) && seen.add(next)) {
            queue.add(next);
        }
    }
}
