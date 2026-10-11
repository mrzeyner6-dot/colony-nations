package com.mrzeyner.colonynations.client;

import com.mrzeyner.colonynations.ColonyNations;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/**
 * Drops the territory cache when the client leaves a world. Not loaded on a dedicated server.
 */
@EventBusSubscriber(modid = ColonyNations.MOD_ID, value = Dist.CLIENT)
public final class TerritoryClientEvents {
    private TerritoryClientEvents() {
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        TerritoryClientCache.clear();
    }
}
