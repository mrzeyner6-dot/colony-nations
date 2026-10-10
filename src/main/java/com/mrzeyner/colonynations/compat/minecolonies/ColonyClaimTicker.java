package com.mrzeyner.colonynations.compat.minecolonies;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;

import com.mrzeyner.colonynations.ColonyNations;
import com.mrzeyner.colonynations.data.NationSavedData;
import com.mrzeyner.colonynations.nation.CityRef;
import com.mrzeyner.colonynations.territory.ChunkRef;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Recalculates claim chunks of nation colonies once a minute. MineColonies has no claim-growth event.
 */
@EventBusSubscriber(modid = ColonyNations.MOD_ID)
public final class ColonyClaimTicker {
    private ColonyClaimTicker() {
    }

    @SubscribeEvent
    public static void refresh(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long gameTime = server.overworld().getGameTime();
        if (gameTime % ColonyClaimCache.REFRESH_TICKS != 0) {
            return;
        }
        NationSavedData data = NationSavedData.get(server);
        if (data.unreadable()) {
            return;
        }
        boolean changed = false;
        for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
            CityRef city = MineColoniesCities.cityRef(colony);
            if (data.registry().byColony(city).isEmpty()) {
                continue;
            }
            for (ChunkRef chunk : ColonyClaimCache.get().refresh(colony, gameTime)) {
                if (data.territory().owner(chunk).isPresent()) {
                    data.territory().release(chunk);
                    changed = true;
                }
            }
        }
        if (changed) {
            data.setDirty();
        }
    }

    @SubscribeEvent
    public static void clear(ServerStoppingEvent event) {
        ColonyClaimCache.get().clear();
    }
}
