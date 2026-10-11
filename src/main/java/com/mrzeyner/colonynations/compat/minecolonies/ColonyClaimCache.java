package com.mrzeyner.colonynations.compat.minecolonies;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.minecolonies.api.colony.IColony;

import com.mrzeyner.colonynations.ColonyNations;
import com.mrzeyner.colonynations.data.NationSavedData;
import com.mrzeyner.colonynations.nation.CityRef;
import com.mrzeyner.colonynations.nation.Nation;
import com.mrzeyner.colonynations.territory.ChunkRef;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Cached claim chunks for colonies that belong to a nation.
 * MineColonies has no claim-growth event, so this refreshes once a minute of server time (1200 ticks).
 */
@EventBusSubscriber(modid = ColonyNations.MOD_ID)
public final class ColonyClaimCache {
    public static final long REFRESH_TICKS = 1200L;

    private static final Map<CityRef, Set<ChunkRef>> CHUNKS = new HashMap<>();

    private ColonyClaimCache() {
    }

    public static void remember(CityRef city, Set<ChunkRef> chunks) {
        CHUNKS.put(city, Set.copyOf(chunks));
    }

    public static void forget(CityRef city) {
        CHUNKS.remove(city);
    }

    public static Set<ChunkRef> chunks(CityRef city) {
        return CHUNKS.getOrDefault(city, Set.of());
    }

    public static void refresh(MinecraftServer server) {
        NationSavedData data = NationSavedData.get(server);
        Set<CityRef> live = new HashSet<>();
        for (Nation nation : data.registry().all()) {
            for (CityRef city : nation.cities()) {
                live.add(city);
                IColony colony = MineColoniesCities.findByCity(city);
                if (colony == null) {
                    CHUNKS.remove(city);
                    continue;
                }
                remember(city, ColonyClaimChunks.of(colony));
            }
        }
        CHUNKS.keySet().retainAll(live);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        long time = event.getServer().overworld().getGameTime();
        if (time % REFRESH_TICKS != 0) {
            return;
        }
        refresh(event.getServer());
    }
}
