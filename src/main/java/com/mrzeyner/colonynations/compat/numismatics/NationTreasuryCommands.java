package com.mrzeyner.colonynations.compat.numismatics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import com.mrzeyner.colonynations.command.Messages;
import com.mrzeyner.colonynations.data.NationSavedData;
import com.mrzeyner.colonynations.economy.CoinChange;
import com.mrzeyner.colonynations.economy.CoinStacks;
import com.mrzeyner.colonynations.nation.Nation;
import com.mrzeyner.colonynations.nation.NationRegistry;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class NationTreasuryCommands {
    private NationTreasuryCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> branch() {
        return Commands.literal("treasury")
                .executes(NationTreasuryCommands::balance)
                .then(Commands.literal("deposit").executes(ctx -> deposit(ctx.getSource())))
                .then(Commands.literal("withdraw")
                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                .executes(ctx -> withdraw(ctx.getSource(), LongArgumentType.getLong(ctx, "amount")))));
    }

    private static int balance(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Nation nation = ownNation(source, saved(source).registry());
        if (nation == null) {
            return 0;
        }
        Messages.header(source, "command.colony_nations.treasury.header");
        Messages.line(source, "command.colony_nations.treasury.nation", nation.name());
        Messages.line(source, "command.colony_nations.treasury.balance", nation.treasury());
        return 1;
    }

    public static int deposit(CommandSourceStack source) throws CommandSyntaxException {
        NationSavedData data = saved(source);
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
            return Messages.error(source, "command.colony_nations.treasury.deposit.empty");
        }
        long value = NumismaticsCoins.stackSpurs(hand);
        long updated;
        try {
            updated = Math.addExact(nation.treasury(), value);
        } catch (ArithmeticException exception) {
            return Messages.error(source, "command.colony_nations.treasury.overflow");
        }
        hand.setCount(0);
        data.registry().setTreasury(nation.id(), updated);
        data.setDirty();
        return Messages.success(source, "command.colony_nations.treasury.deposit.success", value, nation.name(), updated);
    }

    public static int depositAll(CommandSourceStack source) throws CommandSyntaxException {
        NationSavedData data = saved(source);
        if (data.refuseChanges(source)) {
            return 0;
        }
        Nation nation = ownNation(source, data.registry());
        if (nation == null) {
            return 0;
        }
        ServerPlayer player = player(source);
        List<ItemStack> coins = coinStacks(player);
        if (coins.isEmpty()) {
            return Messages.error(source, "command.colony_nations.treasury.deposit.none");
        }
        long accepted = 0L;
        List<ItemStack> taken = new ArrayList<>();
        for (ItemStack stack : coins) {
            long value = NumismaticsCoins.stackSpurs(stack);
            if (value <= 0L) {
                continue;
            }
            long next;
            try {
                next = Math.addExact(accepted, value);
                Math.addExact(nation.treasury(), next);
            } catch (ArithmeticException overflow) {
                break;
            }
            accepted = next;
            taken.add(stack);
        }
        if (taken.isEmpty()) {
            return Messages.error(source, "command.colony_nations.treasury.overflow");
        }
        for (ItemStack stack : taken) {
            stack.setCount(0);
        }
        long updated = nation.treasury() + accepted;
        data.registry().setTreasury(nation.id(), updated);
        data.setDirty();
        return Messages.success(source, "command.colony_nations.treasury.deposit.all", accepted, nation.name(), updated);
    }

    public static int withdraw(CommandSourceStack source, long amount) throws CommandSyntaxException {
        NationSavedData data = saved(source);
        if (data.refuseChanges(source)) {
            return 0;
        }
        Nation nation = ownNation(source, data.registry());
        if (nation == null) {
            return 0;
        }
        if (amount < 1L) {
            return Messages.error(source, "command.colony_nations.treasury.withdraw.amount");
        }
        if (amount > nation.treasury()) {
            return Messages.error(source, "command.colony_nations.treasury.withdraw.insufficient", nation.name(), nation.treasury());
        }
        List<CoinChange.Denomination> denominations = NumismaticsCoins.denominations();
        Map<String, Integer> stackSizes = NumismaticsCoins.maxStackById();
        CoinChange.Exchange exchange = CoinChange.exchange(denominations, amount);
        long paid = amount - exchange.remainderSpurs();
        if (paid <= 0) {
            return Messages.error(source, "command.colony_nations.treasury.withdraw.unpayable");
        }
        ServerPlayer player = player(source);
        List<Integer> empties = emptyCoinSlots(player);
        long needed = CoinStacks.stacksNeeded(exchange.coins(), stackSizes);
        if (needed > empties.size()) {
            long max = CoinStacks.maxFitting(denominations, stackSizes, empties.size(), nation.treasury());
            return Messages.error(source, "command.colony_nations.treasury.withdraw.no_space", max, empties.size());
        }
        List<ItemStack> payout = NumismaticsCoins.stacksFor(exchange.coins());
        for (int i = 0; i < payout.size(); i++) {
            player.getInventory().setItem(empties.get(i), payout.get(i));
        }
        long updated = nation.treasury() - paid;
        data.registry().setTreasury(nation.id(), updated);
        data.setDirty();
        return Messages.success(source, "command.colony_nations.treasury.withdraw.success", paid, nation.name(), updated);
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

    private static List<ItemStack> coinStacks(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        List<ItemStack> coins = new ArrayList<>();
        for (ItemStack stack : inventory.items) {
            if (NumismaticsCoins.isCoin(stack)) {
                coins.add(stack);
            }
        }
        for (ItemStack stack : inventory.offhand) {
            if (NumismaticsCoins.isCoin(stack)) {
                coins.add(stack);
            }
        }
        return coins;
    }

    private static Nation ownNation(CommandSourceStack source, NationRegistry registry) throws CommandSyntaxException {
        ServerPlayer player = player(source);
        if (player == null) {
            Messages.error(source, "command.colony_nations.players_only");
            return null;
        }
        Nation nation = registry.byOwner(player.getUUID()).orElse(null);
        if (nation == null) {
            Messages.error(source, "command.colony_nations.info.none");
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

    private static NationSavedData saved(CommandSourceStack source) {
        return NationSavedData.get(source.getServer());
    }
}
