package com.mrzeyner.colonynations.trade;

import com.mrzeyner.colonynations.data.NationSavedData;
import com.mrzeyner.colonynations.territory.Upkeep;

import net.minecraft.server.MinecraftServer;

/**
 * Announces yesterday's pack sales in the same daily pass as tax and upkeep.
 */
public final class TradeNotices {
    private TradeNotices() {
    }

    public static void flush(MinecraftServer server) {
        NationSavedData data = NationSavedData.get(server);
        if (data.unreadable()) {
            return;
        }
        long today = server.overworld().getDayTime() / Upkeep.TICKS_PER_DAY;
        data.flushTrade(today);
    }
}