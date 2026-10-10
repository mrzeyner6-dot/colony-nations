package com.mrzeyner.colonynations;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Client entry point. This class is not loaded on a dedicated server.
 */
@Mod(value = ColonyNations.MOD_ID, dist = Dist.CLIENT)
public class ColonyNationsClient {
    public ColonyNationsClient(ModContainer container) {
        ColonyNations.LOGGER.info("Colony Nations client setup");
    }
}
