package com.mrzeyner.colonynations.compat.journeymap;

import com.mrzeyner.colonynations.ColonyNations;

import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Loads the map plugin class only when JourneyMap is installed.
 * That class references JourneyMap types, so a dedicated server or a client
 * without the map mod must never resolve it. JourneyMap finds the plugin by
 * its annotation and constructs it; this method is the explicit gate.
 * Registered from {@code ColonyNations} on the mod bus. Client setup does not run on a dedicated server.
 */
public final class JourneyMapGate {
    private static final String PLUGIN = "com.mrzeyner.colonynations.compat.journeymap.ColonyNationsMapPlugin";

    private JourneyMapGate() {
    }

    public static void onClientSetup(FMLClientSetupEvent event) {
        if (!ModList.get().isLoaded("journeymap")) {
            return;
        }
        event.enqueueWork(JourneyMapGate::loadPlugin);
    }

    private static void loadPlugin() {
        try {
            Class.forName(PLUGIN);
        } catch (ClassNotFoundException exception) {
            ColonyNations.LOGGER.error("JourneyMap is installed, but the map plugin class is missing", exception);
        }
    }
}
