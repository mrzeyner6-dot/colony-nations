package com.mrzeyner.colonynations.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private static final List<Update> LISTENERS = new ArrayList<>();
    private static String dimension = "";

    /**
     * Told after the cache changes. {@code full} means every overlay should be rebuilt
     * (a snapshot, a dimension change, or a logout). Otherwise only {@code nations} changed.
     */
    public interface Update {
        void onTerritory(Set<UUID> nations, boolean full);
    }

    private TerritoryClientCache() {
    }

    public static void listen(Update update) {
        LISTENERS.add(update);
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
        notifyListeners(Set.copyOf(NATIONS.keySet()), true);
    }

    public static void apply(TerritoryDeltaPayload delta) {
        Set<UUID> changed = new HashSet<>();
        for (NationStamp nation : delta.nations()) {
            NATIONS.put(nation.id(), nation);
            changed.add(nation.id());
        }
        for (UUID removed : delta.removedNations()) {
            NATIONS.remove(removed);
            PLOTS.entrySet().removeIf(entry -> removed.equals(entry.getValue().nation()));
            changed.add(removed);
        }
        for (ChunkPlot plot : delta.plots()) {
            ChunkRef chunk = new ChunkRef(dimension, plot.x(), plot.z());
            ChunkPlot previous = PLOTS.get(chunk);
            if (previous != null) {
                changed.add(previous.nation());
            }
            if (plot.release()) {
                PLOTS.remove(chunk);
            } else {
                PLOTS.put(chunk, plot);
                changed.add(plot.nation());
            }
        }
        notifyListeners(changed, false);
    }

    public static void clear() {
        NATIONS.clear();
        PLOTS.clear();
        dimension = "";
        notifyListeners(Set.of(), true);
    }

    private static void notifyListeners(Set<UUID> nations, boolean full) {
        if (LISTENERS.isEmpty()) {
            return;
        }
        List<Update> listeners = List.copyOf(LISTENERS);
        Set<UUID> ids = Set.copyOf(nations);
        for (Update update : listeners) {
            update.onTerritory(ids, full);
        }
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
