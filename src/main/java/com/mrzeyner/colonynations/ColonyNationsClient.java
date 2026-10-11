package com.mrzeyner.colonynations;

import com.mrzeyner.colonynations.client.NationKeys;
import com.mrzeyner.colonynations.client.NationMenuClient;
import com.mrzeyner.colonynations.client.TerminalCreditsClient;
import com.mrzeyner.colonynations.client.TradeTerminalScreen;
import com.mrzeyner.colonynations.network.NationOverviewPayload;
import com.mrzeyner.colonynations.network.TerminalCreditsPayload;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Client entry point. This class is not loaded on a dedicated server.
 */
@Mod(value = ColonyNations.MOD_ID, dist = Dist.CLIENT)
public class ColonyNationsClient {
    public ColonyNationsClient(IEventBus modEventBus, ModContainer container) {
        modEventBus.addListener(NationKeys::register);
        modEventBus.addListener(ColonyNationsClient::registerMenu);
        modEventBus.addListener(TradeTerminalScreen::register);
        ColonyNations.LOGGER.info("Colony Nations client setup");
    }

    private static void registerMenu(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(
                NationOverviewPayload.TYPE,
                NationOverviewPayload.STREAM_CODEC,
                NationMenuClient::handle)
                .playToClient(
                        TerminalCreditsPayload.TYPE,
                        TerminalCreditsPayload.STREAM_CODEC,
                        TerminalCreditsClient::handle);
    }
}
