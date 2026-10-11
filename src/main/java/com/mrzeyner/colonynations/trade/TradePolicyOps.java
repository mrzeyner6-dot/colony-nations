package com.mrzeyner.colonynations.trade;

import java.util.UUID;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import com.mrzeyner.colonynations.Config;
import com.mrzeyner.colonynations.command.Messages;
import com.mrzeyner.colonynations.command.NationOperations;
import com.mrzeyner.colonynations.data.NationSavedData;
import com.mrzeyner.colonynations.nation.Nation;

import net.minecraft.commands.CommandSourceStack;

/**
 * Tariff and embargo changes. The nation menu calls the same methods a command would.
 */
public final class TradePolicyOps {
    private TradePolicyOps() {
    }

    public static int setDefault(CommandSourceStack source, int tariff) throws CommandSyntaxException {
        NationSavedData data = NationSavedData.get(source.getServer());
        if (data.refuseChanges(source)) {
            return 0;
        }
        Nation nation = NationOperations.resolveMutable(source, data.registry(), null);
        if (nation == null) {
            return 0;
        }
        int max = Config.MAX_TARIFF_PERCENT.get();
        data.setTradePolicy(nation.id(), data.tradePolicy(nation.id()).withDefault(tariff, max));
        return 1;
    }

    public static int bump(CommandSourceStack source, String buyerId, int delta) throws CommandSyntaxException {
        return edit(source, buyerId, policy -> policy.bump(buyer(buyerId), clampDelta(delta), Config.MAX_TARIFF_PERCENT.get()));
    }

    public static int toggleEmbargo(CommandSourceStack source, String buyerId) throws CommandSyntaxException {
        return edit(source, buyerId, policy -> policy.toggleEmbargo(buyer(buyerId), Config.MAX_TARIFF_PERCENT.get()));
    }

    private static int edit(CommandSourceStack source, String buyerId, java.util.function.UnaryOperator<TradePolicy> edit)
            throws CommandSyntaxException {
        NationSavedData data = NationSavedData.get(source.getServer());
        if (data.refuseChanges(source)) {
            return 0;
        }
        Nation nation = NationOperations.resolveMutable(source, data.registry(), null);
        if (nation == null) {
            return 0;
        }
        UUID buyer = buyer(buyerId);
        if (buyer == null || data.registry().byId(buyer).isEmpty() || buyer.equals(nation.id())) {
            return Messages.error(source, "command.colony_nations.trade.unknown");
        }
        data.setTradePolicy(nation.id(), edit.apply(data.tradePolicy(nation.id())));
        return 1;
    }

    private static UUID buyer(String buyerId) {
        if (buyerId == null || buyerId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(buyerId);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private static int clampDelta(int delta) {
        if (delta > 1000) {
            return 1000;
        }
        if (delta < -1000) {
            return -1000;
        }
        return delta;
    }
}
