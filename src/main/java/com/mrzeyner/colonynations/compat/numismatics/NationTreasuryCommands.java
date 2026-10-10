package com.mrzeyner.colonynations.compat.numismatics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import com.mrzeyner.colonynations.data.NationSavedData;
import com.mrzeyner.colonynations.economy.CoinChange;
import com.mrzeyner.colonynations.economy.CoinStacks;
import com.mrzeyner.colonynations.nation.Nation;
import com.mrzeyner.colonynations.nation.NationRegistry;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class NationTreasuryCommands {
    private NationTreasuryCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> branch() {
        return Commands.literal("treasury")
                .executes(NationTreasuryCommands::balance)
                .then(Commands.literal("deposit").executes(NationTreasuryCommands::deposit))
                .then(Commands.literal("withdraw")
                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                .executes(NationTreasuryCommands::withdraw)));
    }

    private static int balance(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Nation nation = ownNation(source, saved(ctx).registry());
        if (nation == null) {
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(
                "command.colony_nations.treasury.balance",
                nation.name(),
                nation.treasury()), false);
        return 1;
    }

    private static int deposit(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        NationSavedData data = saved(ctx);
        if (data.refuseChanges(source)) {
            return 0;
        }
        Nation nation = ownNation(source, data.registry());
        if (nation == null) {
            return 0;
        }
        ServerPlayer player = player(source);
        ItemStack hand = player.getMainHandItem();
        if (!NumismaticsCoins.isCoin(hand)) {
            return fail(source, "command.colony_nations.treasury.deposit.empty");
        }
        long value = NumismaticsCoins.stackSpurs(hand);
        long updated;
        try {
            updated = Math.addExact(nation.treasury(), value);
        } catch (ArithmeticException exception) {
            return fail(source, "command.colony_nations.treasury.overflow");
        }
        hand.setCount(0);
        data.registry().setTreasury(nation.id(), updated);
        data.setDirty();
        return ok(source, "command.colony_nations.treasury.deposit.success", value, nation.name(), updated);
    }

    private static int withdraw(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        NationSavedData data = saved(ctx);
        if (data.refuseChanges(source)) {
            return 0;
        }
        Nation nation = ownNation(source, data.registry());
        if (nation == null) {
            return 0;
        }
        long amount = LongArgumentType.getLong(ctx, "amount");
        if (amount > nation.treasury()) {
            return fail(source, "command.colony_nations.treasury.withdraw.insufficient", nation.name(), nation.treasury());
        }
        List<CoinChange.Denomination> denominations = NumismaticsCoins.denominations();
        Map<String, Integer> stackSizes = NumismaticsCoins.maxStackById();
        CoinChange.Exchange exchange = CoinChange.exchange(denominations, amount);
        long paid = amount - exchange.remainderSpurs();
        if (paid <= 0) {
            return fail(source, "command.colony_nations.treasury.withdraw.unpayable");
        }
        ServerPlayer player = player(source);
        List<Integer> empties = emptyCoinSlots(player);
        long needed = CoinStacks.stacksNeeded(exchange.coins(), stackSizes);
        if (needed > empties.size()) {
            long max = CoinStacks.maxFitting(denominations, stackSizes, empties.size(), nation.treasury());
            return fail(source, "command.colony_nations.treasury.withdraw.no_space", max, empties.size());
        }
        List<ItemStack> payout = NumismaticsCoins.stacksFor(exchange.coins());
        for (int i = 0; i < payout.size(); i++) {
            player.getInventory().setItem(empties.get(i), payout.get(i));
        }
        long updated = nation.treasury() - paid;
        data.registry().setTreasury(nation.id(), updated);
        data.setDirty();
        return ok(source, "command.colony_nations.treasury.withdraw.success", paid, nation.name(), updated);
    }

    /**
     * Empty main-inventory and offhand slots. Armor slots cannot hold coins.
     */
    private static List<Integer> emptyCoinSlots(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < inventory.items.size(); i++) {
            if (inventory.items.get(i).isEmpty()) {
                slots.add(i);
            }
        }
        for (int i = 0; i < inventory.offhand.size(); i++) {
            if (inventory.offhand.get(i).isEmpty()) {
                slots.add(Inventory.SLOT_OFFHAND + i);
            }
        }
        return slots;
    }

    private static Nation ownNation(CommandSourceStack source, NationRegistry registry) throws CommandSyntaxException {
        ServerPlayer player = player(source);
        if (player == null) {
            fail(source, "command.colony_nations.players_only");
            return null;
        }
        Nation nation = registry.byOwner(player.getUUID()).orElse(null);
        if (nation == null) {
            fail(source, "command.colony_nations.info.none");
            return null;
        }
        return nation;
    }

    private static ServerPlayer player(CommandSourceStack source) throws CommandSyntaxException {
        if (source.getEntity() instanceof ServerPlayer serverPlayer) {
            return serverPlayer;
        }
        if (source.getEntity() == null) {
            return null;
        }
        return source.getPlayerOrException();
    }

    private static NationSavedData saved(CommandContext<CommandSourceStack> ctx) {
        return NationSavedData.get(ctx.getSource().getServer());
    }

    private static int ok(CommandSourceStack source, String key, Object... args) {
        source.sendSuccess(() -> Component.translatable(key, args), true);
        return 1;
    }

    private static int fail(CommandSourceStack source, String key, Object... args) {
        source.sendFailure(Component.translatable(key, args));
        return 0;
    }
}
