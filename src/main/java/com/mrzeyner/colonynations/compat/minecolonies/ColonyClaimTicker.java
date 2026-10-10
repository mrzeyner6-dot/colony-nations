package com.mrzeyner.colonynations.compat.minecolonies;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;

import com.mrzeyner.colonynations.ColonyNations;
import com.mrzeyner.colonynations.data.NationSavedData;
import com.mrzeyner.colonynations.nation.CityRef;
import com.mrzeyner.colonynations.nation.Nation;
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
            Nation nation = data.registry().byColony(city).orElse(null);
            if (nation == null) {
                continue;
            }
            String previousName = ColonyClaimCache.get().name(city);
            Set<ChunkRef> before = ColonyClaimCache.get().peek(city);
            Set<ChunkRef> after = ColonyClaimCache.get().refresh(colony, gameTime);
            String name = ColonyClaimCache.get().name(city);
            boolean nameChanged = !name.equals(previousName);
            List<ChunkRef> showCity = new ArrayList<>();
            List<ChunkRef> left = new ArrayList<>();
            for (ChunkRef chunk : after) {
                boolean coveredState = data.territory().owner(chunk).isPresent();
                if (coveredState) {
                    data.territory().release(chunk);
                    changed = true;
                }
                if (!before.contains(chunk) || coveredState) {
                    showCity.add(chunk);
                }
            }
            for (ChunkRef chunk : before) {
                if (!after.contains(chunk)) {
                    left.add(chunk);
                }
            }
            if (nameChanged) {
                showCity = new ArrayList<>(after);
            }
            TerritorySync.broadcastCity(server, nation, showCity, colony.getID(), name);
            TerritorySync.reconcile(server, data, left, gameTime);
            if (nameChanged || !showCity.isEmpty() || !left.isEmpty()) {
                TerritorySync.relabelState(server, data, nation, city.dimension(), gameTime);
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
