package com.mrzeyner.colonynations.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mrzeyner.colonynations.network.ChunkPlot;
import com.mrzeyner.colonynations.network.NationStamp;
import com.mrzeyner.colonynations.network.TerritoryDeltaPayload;
import com.mrzeyner.colonynations.network.TerritorySnapshotPayload;
import com.mrzeyner.colonynations.territory.ChunkRef;

/**
 * What the client knows about nations and the dimension the player is in.
 * JourneyMap is not required; the cache just stays in memory.
 */
public final class TerritoryClientCache {
    private static final Map<UUID, NationStamp> NATIONS = new HashMap<>();
    private static final Map<ChunkRef, ChunkPlot> PLOTS = new HashMap<>();
    private static String dimension = "";

    private TerritoryClientCache() {
    }

    public static void replace(String dimensionId, TerritorySnapshotPayload snapshot) {
        dimension = dimensionId;
        NATIONS.clear();
        PLOTS.clear();
        for (NationStamp nation : snapshot.nations()) {
            NATIONS.put(nation.id(), nation);
        }
        for (ChunkPlot plot : snapshot.plots()) {
            PLOTS.put(new ChunkRef(dimensionId, plot.x(), plot.z()), plot);
        }
    }

    public static void apply(TerritoryDeltaPayload delta) {
        for (NationStamp nation : delta.nations()) {
            NATIONS.put(nation.id(), nation);
        }
        for (UUID removed : delta.removedNations()) {
            NATIONS.remove(removed);
            PLOTS.entrySet().removeIf(entry -> removed.equals(entry.getValue().nation()));
        }
        for (ChunkPlot plot : delta.plots()) {
            ChunkRef chunk = new ChunkRef(dimension, plot.x(), plot.z());
            if (plot.release()) {
                PLOTS.remove(chunk);
            } else {
                PLOTS.put(chunk, plot);
            }
        }
    }

    public static void clear() {
        NATIONS.clear();
        PLOTS.clear();
        dimension = "";
    }

    public static String dimension() {
        return dimension;
    }

    public static Map<UUID, NationStamp> nations() {
        return Map.copyOf(NATIONS);
    }

    public static Map<ChunkRef, ChunkPlot> plots() {
        return Map.copyOf(PLOTS);
    }
}
