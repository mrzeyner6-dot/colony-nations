package com.mrzeyner.colonynations.territory;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Splits nation land into state polygons and city polygons that do not share a chunk.
 * A city claim wins over state land, including after the claim grows onto chunks that used to be state.
 */
public final class MapLayers {
    public record Split(
            Set<ChunkRef> state,
            Map<Integer, Set<ChunkRef>> cities,
            List<ChunkPolygons.Polygon> statePolygons,
            Map<Integer, List<ChunkPolygons.Polygon>> cityPolygons) {
    }

    private MapLayers() {
    }

    public static Split of(Collection<ChunkRef> state, Map<Integer, ? extends Collection<ChunkRef>> cities) {
        Map<ChunkRef, Integer> winner = new LinkedHashMap<>();
        if (cities != null) {
            for (Map.Entry<Integer, ? extends Collection<ChunkRef>> entry : cities.entrySet()) {
                if (entry.getValue() == null) {
                    continue;
                }
                int colonyId = entry.getKey() == null ? Integer.MAX_VALUE : entry.getKey();
                for (ChunkRef chunk : entry.getValue()) {
                    if (chunk != null) {
                        winner.merge(chunk, colonyId, Math::min);
                    }
                }
            }
        }
        Map<Integer, Set<ChunkRef>> cityOnly = new LinkedHashMap<>();
        for (Map.Entry<ChunkRef, Integer> entry : winner.entrySet()) {
            cityOnly.computeIfAbsent(entry.getValue(), id -> new HashSet<>()).add(entry.getKey());
        }
        Set<ChunkRef> blocked = new HashSet<>();
        for (Set<ChunkRef> claim : cityOnly.values()) {
            blocked.addAll(claim);
        }
        Set<ChunkRef> stateOnly = new HashSet<>();
        if (state != null) {
            for (ChunkRef chunk : state) {
                if (chunk != null && !blocked.contains(chunk)) {
                    stateOnly.add(chunk);
                }
            }
        }
        Map<Integer, List<ChunkPolygons.Polygon>> cityPolygons = new LinkedHashMap<>();
        for (Map.Entry<Integer, Set<ChunkRef>> entry : cityOnly.entrySet()) {
            cityPolygons.put(entry.getKey(), ChunkPolygons.of(entry.getValue()));
        }
        return new Split(Set.copyOf(stateOnly), copyCities(cityOnly), ChunkPolygons.excluding(stateOnly, blocked), cityPolygons);
    }

    public static Set<ChunkRef> covered(List<ChunkPolygons.Polygon> polygons, Collection<ChunkRef> candidates) {
        Set<ChunkRef> inside = new HashSet<>();
        if (polygons == null || candidates == null) {
            return inside;
        }
        for (ChunkRef chunk : candidates) {
            if (chunk == null) {
                continue;
            }
            double x = chunk.x() + 0.5;
            double z = chunk.z() + 0.5;
            for (ChunkPolygons.Polygon polygon : polygons) {
                if (ChunkPolygons.covers(polygon, x, z)) {
                    inside.add(chunk);
                    break;
                }
            }
        }
        return inside;
    }

    private static Map<Integer, Set<ChunkRef>> copyCities(Map<Integer, Set<ChunkRef>> cities) {
        Map<Integer, Set<ChunkRef>> copy = new LinkedHashMap<>();
        for (Map.Entry<Integer, Set<ChunkRef>> entry : cities.entrySet()) {
            copy.put(entry.getKey(), Set.copyOf(entry.getValue()));
        }
        return Map.copyOf(copy);
    }
}
