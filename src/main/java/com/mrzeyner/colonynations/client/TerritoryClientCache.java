package com.mrzeyner.colonynations.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mrzeyner.colonynations.territory.ChunkRef;

/**
 * Last territory snapshot for the local player's dimension. Unused until JourneyMap reads it.
 */
public final class TerritoryClientCache {
    private static String dimension = "";
    private static final Map<UUID, NationView> NATIONS = new HashMap<>();
    private static final Map<ChunkRef, Plot> PLOTS = new HashMap<>();

    private TerritoryClientCache() {
    }

    public record NationView(UUID id, String name, int color) {
    }

    public record Plot(UUID nation, boolean city) {
    }

    public static void replace(String nextDimension, Map<UUID, NationView> nations, Map<ChunkRef, Plot> plots) {
        dimension = nextDimension;
        NATIONS.clear();
        NATIONS.putAll(nations);
        PLOTS.clear();
        PLOTS.putAll(plots);
    }

    public static void upsertNation(UUID id, String name, int color) {
        NATIONS.put(id, new NationView(id, name, color));
    }

    public static void removeNation(UUID id) {
        NATIONS.remove(id);
        PLOTS.entrySet().removeIf(entry -> id.equals(entry.getValue().nation()));
    }

    public static void putPlot(String plotDimension, ChunkRef chunk, Plot plot) {
        if (!plotDimension.equals(dimension)) {
            return;
        }
        PLOTS.put(chunk, plot);
    }

    public static void removePlot(String plotDimension, ChunkRef chunk) {
        if (!plotDimension.equals(dimension)) {
            return;
        }
        PLOTS.remove(chunk);
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
