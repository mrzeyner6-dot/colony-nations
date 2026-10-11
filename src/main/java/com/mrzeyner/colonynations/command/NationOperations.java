package com.mrzeyner.colonynations.command;

import java.util.Locale;
import java.util.UUID;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import com.mrzeyner.colonynations.Config;
import com.mrzeyner.colonynations.compat.minecolonies.CitizenTaxHappiness;
import com.mrzeyner.colonynations.compat.minecolonies.MineColoniesCities;
import com.mrzeyner.colonynations.compat.minecolonies.NationCityCommands;
import com.mrzeyner.colonynations.compat.minecolonies.NationTaxCommands;
import com.mrzeyner.colonynations.compat.minecolonies.NationTerritoryCommands;
import com.mrzeyner.colonynations.compat.minecolonies.Secession;
import com.mrzeyner.colonynations.compat.minecolonies.TerritorySync;
import com.mrzeyner.colonynations.compat.numismatics.NationTreasuryCommands;
import com.mrzeyner.colonynations.data.NationSavedData;
import com.mrzeyner.colonynations.nation.CityRef;
import com.mrzeyner.colonynations.nation.Nation;
import com.mrzeyner.colonynations.nation.NationException;
import com.mrzeyner.colonynations.nation.NationRegistry;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/**
 * Owner actions shared by {@code /nation} and the nation menu.
 * Commands and the menu both call these methods. Each one checks the same rights as the command.
 */
public final class NationOperations {
    private NationOperations() {
    }

    public static int create(CommandSourceStack source, String name) throws CommandSyntaxException {
        NationSavedData data = saved(source);
        if (data.refuseChanges(source)) {
            return 0;
        }
        ServerPlayer player = player(source);
        if (player == null) {
            return Messages.error(source, "command.colony_nations.players_only");
        }
        NationRegistry registry = data.registry();
        try {
            Nation nation = registry.create(name, player.getUUID(), 0xFFFFFF, source.getLevel().getGameTime());
            int taxRate = NationTaxCommands.foundingRate();
            if (taxRate != nation.taxRate()) {
                registry.setTax(nation.id(), taxRate, null);
            }
            data.setDirty();
            return Messages.event(source, "command.colony_nations.create.success", nation.name());
        } catch (NationException exception) {
            return failRule(source, exception);
        }
    }

    /**
     * Chat confirmation used by {@code /nation disband}. The menu confirms in its own window and calls {@link #erase}.
     */
    public static int disband(CommandSourceStack source, String nationName, DisbandConfirmations confirms) throws CommandSyntaxException {
        NationSavedData data = saved(source);
        if (data.refuseChanges(source)) {
            return 0;
        }
        Nation nation = resolveMutable(source, data.registry(), nationName);
        if (nation == null) {
            return 0;
        }
        long windowTicks = Config.DISBAND_CONFIRM_SECONDS.get() * 20L;
        String key = actorKey(source) + ":" + nation.id();
        if (!confirms.confirm(key, source.getLevel().getGameTime(), windowTicks)) {
            return Messages.info(
                    source,
                    "command.colony_nations.disband.confirm",
                    nation.name(),
                    Config.DISBAND_CONFIRM_SECONDS.get());
        }
        return erase(source, nation);
    }

    /**
     * Removes the sender's nation. The caller has already confirmed.
     */
    public static int disbandNow(CommandSourceStack source) throws CommandSyntaxException {
        NationSavedData data = saved(source);
        if (data.refuseChanges(source)) {
            return 0;
        }
        Nation nation = resolveMutable(source, data.registry(), null);
        if (nation == null) {
            return 0;
        }
        return erase(source, nation);
    }

    public static int erase(CommandSourceStack source, Nation nation) {
        NationSavedData data = saved(source);
        for (CityRef city : nation.cities()) {
            CitizenTaxHappiness.clear(MineColoniesCities.findByCity(city));
        }
        Secession.dropNation(data, nation.id());
        TerritorySync.onDisband(source.getServer(), nation);
        data.registry().delete(nation.id());
        data.territory().releaseAll(nation.id());
        data.setDirty();
        return Messages.success(source, "command.colony_nations.disband.success", nation.name());
    }

    public static int rename(CommandSourceStack source, String nationName, String newName) throws CommandSyntaxException {
        NationSavedData data = saved(source);
        if (data.refuseChanges(source)) {
            return 0;
        }
        Nation nation = resolveMutable(source, data.registry(), nationName);
        if (nation == null) {
            return 0;
        }
        String previous = nation.name();
        try {
            Nation renamed = data.registry().rename(nation.id(), newName);
            data.setDirty();
            TerritorySync.nationMeta(source.getServer(), renamed);
            return Messages.success(source, "command.colony_nations.rename.success", previous, renamed.name());
        } catch (NationException exception) {
            return failRule(source, exception);
        }
    }

    public static int color(CommandSourceStack source, String nationName, String rawColor) throws CommandSyntaxException {
        if (saved(source).refuseChanges(source)) {
            return 0;
        }
        Integer rgb = NationColors.parse(rawColor);
        if (rgb == null) {
            return Messages.error(source, "command.colony_nations.color.invalid");
        }
        NationSavedData data = saved(source);
        Nation nation = resolveMutable(source, data.registry(), nationName);
        if (nation == null) {
            return 0;
        }
        Nation colored = data.registry().setColor(nation.id(), rgb);
        data.setDirty();
        TerritorySync.nationMeta(source.getServer(), colored);
        return Messages.success(source, "command.colony_nations.color.success", colored.name(), formatColor(rgb));
    }

    public static int makeCapital(CommandSourceStack source, String dimension, int colonyId) throws CommandSyntaxException {
        return NationCityCommands.makeCapital(source, dimension, colonyId);
    }

    public static int removeCity(CommandSourceStack source, String dimension, int colonyId) throws CommandSyntaxException {
        return NationCityCommands.removeCity(source, dimension, colonyId);
    }

    public static int annex(CommandSourceStack source) throws CommandSyntaxException {
        return NationTerritoryCommands.annex(source);
    }

    public static int unannex(CommandSourceStack source) throws CommandSyntaxException {
        return NationTerritoryCommands.unannex(source);
    }

    public static int depositHand(CommandSourceStack source) throws CommandSyntaxException {
        return NationTreasuryCommands.deposit(source);
    }

    public static int depositAll(CommandSourceStack source) throws CommandSyntaxException {
        return NationTreasuryCommands.depositAll(source);
    }

    public static int withdraw(CommandSourceStack source, long amount) throws CommandSyntaxException {
        return NationTreasuryCommands.withdraw(source, amount);
    }

    public static int setTax(CommandSourceStack source, int rate) throws CommandSyntaxException {
        return NationTaxCommands.setRate(source, rate);
    }

    public static int acceptOffer(CommandSourceStack source, String dimension, int colonyId) throws CommandSyntaxException {
        return Secession.respond(source, dimension, colonyId, true);
    }

    public static int declineOffer(CommandSourceStack source, String dimension, int colonyId) throws CommandSyntaxException {
        return Secession.respond(source, dimension, colonyId, false);
    }

    public static int buyCity(CommandSourceStack source, String raw) throws CommandSyntaxException {
        return com.mrzeyner.colonynations.compat.minecolonies.FreeCityJoin.buy(source, raw);
    }

    public static int buyCity(CommandSourceStack source, String dimension, int colonyId) throws CommandSyntaxException {
        return com.mrzeyner.colonynations.compat.minecolonies.FreeCityJoin.buyAt(source, dimension, colonyId);
    }

    public static int giveCity(CommandSourceStack source, String raw, String nationName) throws CommandSyntaxException {
        return com.mrzeyner.colonynations.compat.minecolonies.FreeCityJoin.give(source, raw, nationName);
    }

    /**
     * @param nationName null when the action targets the sender's own nation
     */
    public static Nation resolveMutable(CommandSourceStack source, NationRegistry registry, String nationName) throws CommandSyntaxException {
        if (nationName != null) {
            Nation nation = registry.byName(nationName).orElse(null);
            if (nation == null) {
                Messages.error(source, "command.colony_nations.unknown", nationName);
            }
            return nation;
        }
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
        if (!nation.owner().equals(player.getUUID())) {
            Messages.error(source, "command.colony_nations.not_owner");
            return null;
        }
        return nation;
    }

    public static ServerPlayer player(CommandSourceStack source) throws CommandSyntaxException {
        if (source.getEntity() instanceof ServerPlayer player) {
            return player;
        }
        if (source.getEntity() == null) {
            return null;
        }
        return source.getPlayerOrException();
    }

    private static String actorKey(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = player(source);
        return player == null ? "console" : player.getUUID().toString();
    }

    private static NationSavedData saved(CommandSourceStack source) {
        return NationSavedData.get(source.getServer());
    }

    private static int failRule(CommandSourceStack source, NationException exception) {
        String key = switch (exception.reason()) {
            case NAME_LENGTH -> "command.colony_nations.name_length";
            case NAME_TAKEN -> "command.colony_nations.name_taken";
            case OWNER_HAS_NATION -> "command.colony_nations.owner_has_nation";
            case UNKNOWN_NATION -> "command.colony_nations.unknown";
            case COLONY_TAKEN, UNKNOWN_COLONY -> "command.colony_nations.unknown";
        };
        return Messages.error(source, key);
    }

    private static String formatColor(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }

    public static String ownerLabel(CommandSourceStack source, UUID owner) {
        ServerPlayer online = source.getServer().getPlayerList().getPlayer(owner);
        return online == null ? owner.toString() : online.getGameProfile().getName();
    }
}
