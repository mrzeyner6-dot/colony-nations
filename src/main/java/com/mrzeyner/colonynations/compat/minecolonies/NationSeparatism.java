package com.mrzeyner.colonynations.compat.minecolonies;

import java.util.ArrayList;
import java.util.List;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import com.minecolonies.api.colony.IColony;

import com.mrzeyner.colonynations.Config;
import com.mrzeyner.colonynations.command.Messages;
import com.mrzeyner.colonynations.data.NationSavedData;
import com.mrzeyner.colonynations.data.PendingNotice;
import com.mrzeyner.colonynations.economy.Separatism;
import com.mrzeyner.colonynations.nation.CityRef;
import com.mrzeyner.colonynations.nation.Nation;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Daily separatism, and the operator command that sets a city's score.
 */
public final class NationSeparatism {
    private NationSeparatism() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> adminBranch() {
        return Commands.literal("city")
                .then(Commands.literal("separatism")
                        .then(Commands.argument("colonyId", IntegerArgumentType.integer(1))
                                .then(Commands.argument("value", IntegerArgumentType.integer(0, 100))
                                        .executes(NationSeparatism::set))))
                .then(NationSecession.secedeBranch());
    }

    public static void advance(MinecraftServer server) {
        NationSavedData data = NationSavedData.get(server);
        if (data.unreadable()) {
            return;
        }
        double threshold = Config.UNREST_THRESHOLD.get();
        double growth = Config.SEPARATISM_GROWTH.get();
        double decay = Config.SEPARATISM_DECAY.get();
        boolean dirty = false;
        for (Nation nation : List.copyOf(data.registry().all())) {
            Nation current = data.registry().byId(nation.id()).orElse(nation);
            for (CityRef city : List.copyOf(current.cities())) {
                IColony colony = MineColoniesCities.findByCity(city);
                if (colony == null) {
                    continue;
                }
                double happiness = colony.getOverallHappiness();
                if (Double.isNaN(happiness)) {
                    continue;
                }
                double before = current.separatism(city);
                Separatism.Day day = Separatism.advance(
                        before,
                        happiness,
                        city.equals(current.capital()),
                        threshold,
                        growth,
                        decay);
                if (day.value() == before && day.stage() == Separatism.stage(before)) {
                    continue;
                }
                if (day.value() != before) {
                    current = data.registry().setSeparatism(current.id(), city, day.value());
                    dirty = true;
                }
                publish(server, data, current, city, colony, before, day);
            }
        }
        if (dirty) {
            data.setDirty();
        }
    }

    private static int set(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        NationSavedData data = NationSavedData.get(source.getServer());
        if (data.refuseChanges(source)) {
            return 0;
        }
        int colonyId = IntegerArgumentType.getInteger(ctx, "colonyId");
        int value = IntegerArgumentType.getInteger(ctx, "value");
        ResourceKey<Level> prefer = source.getEntity() instanceof ServerPlayer player
                ? player.level().dimension()
                : null;
        Match match = find(data, colonyId, prefer);
        if (match.ambiguous) {
            return Messages.error(source, "command.colony_nations.separatism.ambiguous", colonyId);
        }
        if (match.nation == null || match.city == null) {
            return Messages.error(source, "command.colony_nations.city.unknown", colonyId);
        }
        IColony colony = MineColoniesCities.findByCity(match.city);
        double before = match.nation.separatism(match.city);
        Nation updated = data.registry().setSeparatism(match.nation.id(), match.city, value);
        data.setDirty();
        Separatism.Day day = Separatism.describe(before, value);
        if (colony != null) {
            publish(source.getServer(), data, updated, match.city, colony, before, day);
        }
        String name = colony == null || colony.getName() == null || colony.getName().isBlank()
                ? "#" + colonyId
                : colony.getName();
        return Messages.success(source, "command.colony_nations.separatism.set", name, Separatism.format(value));
    }

    private static void publish(
            MinecraftServer server,
            NationSavedData data,
            Nation nation,
            CityRef city,
            IColony colony,
            double before,
            Separatism.Day day) {
        boolean wasUnrest = Separatism.stage(before) == Separatism.Stage.UNREST;
        boolean nowUnrest = day.stage() == Separatism.Stage.UNREST;
        if (wasUnrest != nowUnrest) {
            String name = colony.getName() == null ? "" : colony.getName();
            TerritorySync.broadcastCity(
                    server,
                    nation,
                    ColonyClaimCache.get().chunks(colony, server.overworld().getGameTime()),
                    city.colonyId(),
                    name);
        }
        String cityName = colony.getName() == null || colony.getName().isBlank()
                ? "#" + city.colonyId()
                : colony.getName();
        if (day.notifyUnrest()) {
            tellOwner(server, data, nation, "command.colony_nations.separatism.unrest", List.of(cityName));
        }
        if (day.notifyMovement()) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                Messages.event(player, "command.colony_nations.separatism.movement", cityName, nation.name());
            }
        }
        if (day.secede()) {
            NationSecession.begin(server, data, nation, city, colony);
        }
    }

    private static void tellOwner(MinecraftServer server, NationSavedData data, Nation nation, String key, List<String> args) {
        ServerPlayer owner = server.getPlayerList().getPlayer(nation.owner());
        if (owner == null) {
            data.addNotice(new PendingNotice(nation.owner(), false, key, args));
            return;
        }
        Messages.event(owner, key, args.toArray());
    }

    private static Match find(NationSavedData data, int colonyId, ResourceKey<Level> prefer) {
        List<Match> matches = new ArrayList<>();
        for (Nation nation : data.registry().all()) {
            for (CityRef city : nation.cities()) {
                if (city.colonyId() == colonyId) {
                    matches.add(new Match(nation, city, false));
                }
            }
        }
        if (matches.isEmpty()) {
            return new Match(null, null, false);
        }
        if (matches.size() == 1) {
            return matches.get(0);
        }
        if (prefer != null) {
            String dimension = prefer.location().toString();
            Match found = null;
            for (Match match : matches) {
                if (!dimension.equals(match.city.dimension())) {
                    continue;
                }
                if (found != null) {
                    return new Match(null, null, true);
                }
                found = match;
            }
            if (found != null) {
                return found;
            }
        }
        return new Match(null, null, true);
    }

    private record Match(Nation nation, CityRef city, boolean ambiguous) {
    }
}
