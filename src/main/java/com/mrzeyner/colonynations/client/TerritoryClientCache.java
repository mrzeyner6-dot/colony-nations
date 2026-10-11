package com.mrzeyner.colonynations.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mrzeyner.colonynations.territory.ChunkRef;

/**
 * Last territory snapshot for the local player's dimension. JourneyMap reads it when that mod is present.
 */
public final class TerritoryClientCache {
    private static String dimension = "";
    private static boolean reset;
    private static final Set<UUID> DIRTY = new HashSet<>();
    private static final Map<UUID, NationView> NATIONS = new HashMap<>();
    private static final Map<ChunkRef, Plot> PLOTS = new HashMap<>();

    private TerritoryClientCache() {
    }

    public record NationView(UUID id, String name, int color) {
    }

    public enum Land {
        STATE,
        CITY,
        CORE
    }

    /**
     * @param colonyId colony id when {@code land} is city or core, otherwise {@code -1}
     * @param placeName colony name for city and core land; empty for annexed land
     * @param unrest city land whose separatism is in the unrest band; the map tooltip gains a suffix
     */
    public record Plot(UUID nation, Land land, int colonyId, String placeName, boolean unrest) {
        public Plot {
            if (placeName == null) {
                placeName = "";
            }
        }
    }

    public static void replace(String nextDimension, Map<UUID, NationView> nations, Map<ChunkRef, Plot> plots) {
        dimension = nextDimension;
        NATIONS.clear();
        NATIONS.putAll(nations);
        PLOTS.clear();
        PLOTS.putAll(plots);
        DIRTY.clear();
        reset = true;
    }

    public static void upsertNation(UUID id, String name, int color) {
        NATIONS.put(id, new NationView(id, name, color));
        DIRTY.add(id);
    }

    public static void removeNation(UUID id) {
        NATIONS.remove(id);
        PLOTS.entrySet().removeIf(entry -> id.equals(entry.getValue().nation()));
        DIRTY.add(id);
    }

    public static void putPlot(String plotDimension, ChunkRef chunk, Plot plot) {
        if (!plotDimension.equals(dimension)) {
            return;
        }
        Plot previous = PLOTS.put(chunk, plot);
        DIRTY.add(plot.nation());
        if (previous != null) {
            DIRTY.add(previous.nation());
        }
    }

    public static void removePlot(String plotDimension, ChunkRef chunk) {
        if (!plotDimension.equals(dimension)) {
            return;
        }
        Plot previous = PLOTS.remove(chunk);
        if (previous != null) {
            DIRTY.add(previous.nation());
        }
    }

    public static boolean consumeReset() {
        boolean value = reset;
        reset = false;
        return value;
    }

    public static Set<UUID> consumeDirty() {
        if (DIRTY.isEmpty()) {
            return Set.of();
        }
        Set<UUID> copy = Set.copyOf(DIRTY);
        DIRTY.clear();
        return copy;
    }

    public static Plot plot(ChunkRef chunk) {
        return PLOTS.get(chunk);
    }

    public static String dimension() {
        return dimension;
    }

    public static Map<UUID, NationView> nations() {
        return Map.copyOf(NATIONS);
    }

    public static Map<ChunkRef, Plot> plots() {
        return Map.copyOf(PLOTS);
    }
}
