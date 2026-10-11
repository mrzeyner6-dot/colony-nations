package com.mrzeyner.colonynations;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import com.mrzeyner.colonynations.compat.minecolonies.NationColonyEvents;
import com.mrzeyner.colonynations.network.TerritoryPayloads;
import com.mrzeyner.colonynations.trade.TradeBlocks;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

@Mod(ColonyNations.MOD_ID)
public class ColonyNations {
    public static final String MOD_ID = "colony_nations";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ColonyNations(IEventBus modEventBus, ModContainer modContainer) {
        TradeBlocks.register(modEventBus);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(TerritoryPayloads::register);
        NeoForge.EVENT_BUS.register(this);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(NationColonyEvents::register);
        LOGGER.info("Colony Nations common setup");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Colony Nations loaded (mod id: {})", MOD_ID);
    }
}
