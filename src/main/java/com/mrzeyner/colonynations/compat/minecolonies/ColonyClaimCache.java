package com.mrzeyner.colonynations.compat.minecolonies;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.minecolonies.api.colony.IColony;

import com.mrzeyner.colonynations.nation.CityRef;
import com.mrzeyner.colonynations.territory.ChunkRef;

/**
 * Cached colony claims. Refreshed when a city joins a nation and again every {@link #REFRESH_TICKS} server ticks,
 * because MineColonies does not post a claim-growth event.
 */
public final class ColonyClaimCache {
    public static final int REFRESH_TICKS = 20 * 60;

    private static final ColonyClaimCache INSTANCE = new ColonyClaimCache();

    private final Map<CityRef, Entry> entries = new HashMap<>();

    private ColonyClaimCache() {
    }

    public static ColonyClaimCache get() {
        return INSTANCE;
    }

    public Set<ChunkRef> chunks(IColony colony, long gameTime) {
        CityRef city = MineColoniesCities.cityRef(colony);
        Entry entry = entries.get(city);
        if (entry != null && gameTime - entry.gameTime < REFRESH_TICKS) {
            return entry.chunks;
        }
        return refresh(colony, gameTime);
    }

    public Set<ChunkRef> peek(CityRef city) {
        Entry entry = entries.get(city);
        return entry == null ? Set.of() : entry.chunks;
    }

    public String name(CityRef city) {
        Entry entry = entries.get(city);
        return entry == null || entry.name == null ? "" : entry.name;
    }

    public Set<ChunkRef> refresh(IColony colony, long gameTime) {
        Set<ChunkRef> chunks = ColonyClaims.of(colony);
        String name = colony.getName() == null ? "" : colony.getName();
        entries.put(MineColoniesCities.cityRef(colony), new Entry(gameTime, chunks, name));
        return chunks;
    }

    public void invalidate(CityRef city) {
        entries.remove(city);
    }

    public void clear() {
        entries.clear();
    }

    private record Entry(long gameTime, Set<ChunkRef> chunks, String name) {
    }
}
