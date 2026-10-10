package com.mrzeyner.colonynations.compat.minecolonies;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;

import com.mrzeyner.colonynations.data.NationSavedData;
import com.mrzeyner.colonynations.nation.Nation;
import com.mrzeyner.colonynations.nation.NationException;
import com.mrzeyner.colonynations.nation.NationRegistry;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /nation city and /nation capital. The player must own both the nation and the colony.
 */
public final class NationCityCommands {
    private NationCityCommands() {
    }

    public static int add(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return changeMembership(ctx, true);
    }

    public static int remove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return changeMembership(ctx, false);
    }

    public static int capital(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = player(source);
        if (player == null) {
            return fail(source, "command.colony_nations.players_only");
        }
        NationSavedData data = NationSavedData.get(source.getServer());
        Nation nation = data.registry().byOwner(player.getUUID()).orElse(null);
        if (nation == null) {
            return fail(source, "command.colony_nations.info.none");
        }
        int colonyId = IntegerArgumentType.getInteger(ctx, "colonyId");
        if (!nation.colonyIds().contains(colonyId)) {
            return fail(source, "command.colony_nations.capital.not_in_nation", colonyId, nation.name());
        }
        data.registry().setCapital(nation.id(), colonyId);
        data.setDirty();
        return ok(source, "command.colony_nations.capital.success", nation.name(), colonyLabel(colonyId), colonyId);
    }

    public static int list(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = player(source);
        if (player == null) {
            return fail(source, "command.colony_nations.players_only");
        }
        Nation nation = NationSavedData.get(source.getServer()).registry().byOwner(player.getUUID()).orElse(null);
        if (nation == null) {
            return fail(source, "command.colony_nations.info.none");
        }
        if (nation.colonyIds().isEmpty()) {
            return fail(source, "command.colony_nations.city.list.empty", nation.name());
        }
        List<Integer> ids = new ArrayList<>(nation.colonyIds());
        ids.sort(Comparator.naturalOrder());
        source.sendSuccess(() -> Component.translatable("command.colony_nations.city.list.header", nation.name()), false);
        for (int colonyId : ids) {
            boolean capital = colonyId == (nation.capitalColonyId() == null ? Integer.MIN_VALUE : nation.capitalColonyId());
            String markKey = capital
                    ? "command.colony_nations.city.capital_mark"
                    : "command.colony_nations.city.no_mark";
            String name = colonyLabel(colonyId);
            source.sendSuccess(() -> Component.translatable(
                    "command.colony_nations.city.list.entry",
                    name,
                    colonyId,
                    Component.translatable(markKey)), false);
        }
        return 1;
    }

    private static int changeMembership(CommandContext<CommandSourceStack> ctx, boolean add) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = player(source);
        if (player == null) {
            return fail(source, "command.colony_nations.players_only");
        }
        int colonyId = IntegerArgumentType.getInteger(ctx, "colonyId");
        IColony colony = findOwned(colonyId, player.getUUID());
        if (colony == null) {
            return missingColony(source, colonyId, player.getUUID());
        }
        NationSavedData data = NationSavedData.get(source.getServer());
        NationRegistry registry = data.registry();
        Nation nation = registry.byOwner(player.getUUID()).orElse(null);
        if (nation == null) {
            return fail(source, "command.colony_nations.info.none");
        }
        try {
            if (add) {
                registry.addColony(nation.id(), colonyId);
            } else if (!nation.colonyIds().contains(colonyId)) {
                return fail(source, "command.colony_nations.capital.not_in_nation", colonyId, nation.name());
            } else {
                registry.removeColony(nation.id(), colonyId);
            }
        } catch (NationException exception) {
            if (exception.reason() == NationException.Reason.COLONY_TAKEN) {
                return fail(source, "command.colony_nations.city.taken", colonyId);
            }
            return fail(source, "command.colony_nations.city.unknown", colonyId);
        }
        data.setDirty();
        String key = add ? "command.colony_nations.city.added" : "command.colony_nations.city.removed";
        return ok(source, key, colony.getName(), colonyId, nation.name());
    }

    private static int missingColony(CommandSourceStack source, int colonyId, UUID owner) {
        IColony any = findById(colonyId);
        if (any == null) {
            return fail(source, "command.colony_nations.city.unknown", colonyId);
        }
        if (!owner.equals(any.getPermissions().getOwner())) {
            return fail(source, "command.colony_nations.city.not_owner", colonyId);
        }
        return fail(source, "command.colony_nations.city.unknown", colonyId);
    }

    static IColony findOwned(int colonyId, UUID owner) {
        for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
            if (colony.getID() == colonyId && owner.equals(colony.getPermissions().getOwner())) {
                return colony;
            }
        }
        return null;
    }

    private static IColony findById(int colonyId) {
        for (IColony colony : IMinecoloniesAPI.getInstance().getColonyManager().getAllColonies()) {
            if (colony.getID() == colonyId) {
                return colony;
            }
        }
        return null;
    }

    private static String colonyLabel(int colonyId) {
        IColony colony = findById(colonyId);
        if (colony == null || colony.getName() == null || colony.getName().isBlank()) {
            return "#" + colonyId;
        }
        return colony.getName();
    }

    private static ServerPlayer player(CommandSourceStack source) throws CommandSyntaxException {
        if (source.getEntity() instanceof ServerPlayer player) {
            return player;
        }
        if (source.getEntity() == null) {
            return null;
        }
        return source.getPlayerOrException();
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
