package com.mrzeyner.colonynations.compat.minecolonies;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;

import com.mrzeyner.colonynations.command.Messages;
import com.mrzeyner.colonynations.data.NationSavedData;
import com.mrzeyner.colonynations.network.TerritorySync;
import com.mrzeyner.colonynations.territory.ChunkRef;
import com.mrzeyner.colonynations.nation.CityRef;
import com.mrzeyner.colonynations.nation.Nation;
import com.mrzeyner.colonynations.nation.NationException;
import com.mrzeyner.colonynations.nation.NationRegistry;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * /nation city and /nation capital. The player must own both the nation and the colony.
 * Commands still take a colony id; the dimension is read from the colony.
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
            return Messages.error(source, "command.colony_nations.players_only");
        }
        NationSavedData data = NationSavedData.get(source.getServer());
        if (data.refuseChanges(source)) {
            return 0;
        }
        Nation nation = data.registry().byOwner(player.getUUID()).orElse(null);
        if (nation == null) {
            return Messages.error(source, "command.colony_nations.info.none");
        }
        int colonyId = IntegerArgumentType.getInteger(ctx, "colonyId");
        CityRef city = MineColoniesCities.member(nation, colonyId, player.level().dimension());
        if (city == null) {
            return Messages.error(source, "command.colony_nations.capital.not_in_nation", colonyId, nation.name());
        }
        data.registry().setCapital(nation.id(), city);
        data.setDirty();
        return Messages.success(source, "command.colony_nations.capital.success", nation.name(), colonyLabel(city), colonyId);
    }

    public static int list(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = player(source);
        if (player == null) {
            return Messages.error(source, "command.colony_nations.players_only");
        }
        Nation nation = NationSavedData.get(source.getServer()).registry().byOwner(player.getUUID()).orElse(null);
        if (nation == null) {
            return Messages.error(source, "command.colony_nations.info.none");
        }
        if (nation.cities().isEmpty()) {
            return Messages.error(source, "command.colony_nations.city.list.empty", nation.name());
        }
        List<CityRef> cities = new ArrayList<>(nation.cities());
        cities.sort(Comparator.comparing(CityRef::dimension).thenComparingInt(CityRef::colonyId));
        Messages.header(source, "command.colony_nations.city.list.header", nation.name());
        for (CityRef city : cities) {
            boolean capital = city.equals(nation.capital());
            String markKey = capital
                    ? "command.colony_nations.city.capital_mark"
                    : "command.colony_nations.city.no_mark";
            Messages.line(
                    source,
                    "command.colony_nations.city.list.entry",
                    colonyLabel(city),
                    city.colonyId(),
                    city.dimension(),
                    Messages.plain(markKey));
        }
        return 1;
    }

    private static int changeMembership(CommandContext<CommandSourceStack> ctx, boolean add) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = player(source);
        if (player == null) {
            return Messages.error(source, "command.colony_nations.players_only");
        }
        if (NationSavedData.get(source.getServer()).refuseChanges(source)) {
            return 0;
        }
        int colonyId = IntegerArgumentType.getInteger(ctx, "colonyId");
        ResourceKey<Level> here = player.level().dimension();
        IColony colony = MineColoniesCities.findOwned(colonyId, player.getUUID(), here);
        if (colony == null) {
            return missingColony(source, colonyId, player.getUUID());
        }
        CityRef city = MineColoniesCities.cityRef(colony);
        NationSavedData data = NationSavedData.get(source.getServer());
        NationRegistry registry = data.registry();
        Nation nation = registry.byOwner(player.getUUID()).orElse(null);
        if (nation == null) {
            return Messages.error(source, "command.colony_nations.info.none");
        }
        try {
            if (add) {
                boolean joined = !nation.cities().contains(city);
                registry.addColony(nation.id(), city);
                if (joined) {
                    NationCityLand.grant(data, nation.id(), colony);
                }
            } else if (!nation.cities().contains(city)) {
                return Messages.error(source, "command.colony_nations.capital.not_in_nation", colonyId, nation.name());
            } else {
                Set<ChunkRef> claim = new HashSet<>(ColonyClaimChunks.of(colony));
                registry.removeColony(nation.id(), city);
                ColonyClaimCache.forget(city);
                claim.removeAll(ColonyClaimChunks.ofNation(data.registry().byId(nation.id()).orElse(nation)));
                TerritorySync.cityChanged(source.getServer(), nation.id(), claim, false, "");
            }
        } catch (NationException exception) {
            if (exception.reason() == NationException.Reason.COLONY_TAKEN) {
                return Messages.error(source, "command.colony_nations.city.taken", colonyId);
            }
            return Messages.error(source, "command.colony_nations.city.unknown", colonyId);
        }
        data.setDirty();
        if (add) {
            return Messages.event(source, "command.colony_nations.city.added", colony.getName(), colonyId, nation.name());
        }
        return Messages.success(source, "command.colony_nations.city.removed", colony.getName(), colonyId, nation.name());
    }

    private static int missingColony(CommandSourceStack source, int colonyId, UUID owner) {
        boolean seen = false;
        boolean owned = false;
        for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
            if (colony.getID() != colonyId) {
                continue;
            }
            seen = true;
            if (owner.equals(colony.getPermissions().getOwner())) {
                owned = true;
            }
        }
        if (seen && !owned) {
            return Messages.error(source, "command.colony_nations.city.not_owner", colonyId);
        }
        return Messages.error(source, "command.colony_nations.city.unknown", colonyId);
    }

    private static String colonyLabel(CityRef city) {
        IColony colony = MineColoniesCities.findByCity(city);
        if (colony == null || colony.getName() == null || colony.getName().isBlank()) {
            return "#" + city.colonyId();
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
}
