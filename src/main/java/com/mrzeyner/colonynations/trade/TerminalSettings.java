package com.mrzeyner.colonynations.trade;

import com.mrzeyner.colonynations.command.Messages;
import com.mrzeyner.colonynations.network.TerminalSettingsPayload;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Applies the price and pack size from the owner window. The same numbers the exchange uses.
 */
public final class TerminalSettings {
    private TerminalSettings() {
    }

    public static void apply(TerminalSettingsPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        Level level = player.level();
        BlockPos pos = payload.pos();
        if (!level.isLoaded(pos) || player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) {
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof TradeTerminalBlockEntity terminal) || !terminal.canManage(player)) {
            Messages.error(player.createCommandSourceStack(), "command.colony_nations.terminal.not_owner");
            return;
        }
        Long price = parse(payload.price(), true);
        Integer pack = parseInt(payload.pack());
        int maxStack = terminal.goods().isEmpty() ? 64 : Math.max(1, terminal.goods().getMaxStackSize());
        if (price == null || pack == null || price < 0L || price > TerminalTrade.MAX_PRICE || pack < 1 || pack > maxStack) {
            Messages.error(player.createCommandSourceStack(), "command.colony_nations.terminal.price_invalid", maxStack);
            return;
        }
        terminal.setOffer(price, pack);
        Messages.success(
                player.createCommandSourceStack(),
                "command.colony_nations.terminal.price_set",
                price,
                pack);
    }

    private static Long parse(String text, boolean allowZero) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return allowZero ? 0L : null;
        }
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException bad) {
            return null;
        }
    }

    private static Integer parseInt(String text) {
        Long value = parse(text, false);
        if (value == null || value > Integer.MAX_VALUE) {
            return null;
        }
        return value.intValue();
    }
}
