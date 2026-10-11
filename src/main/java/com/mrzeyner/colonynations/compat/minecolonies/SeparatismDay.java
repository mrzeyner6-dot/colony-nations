package com.mrzeyner.colonynations.compat.minecolonies;

import java.util.ArrayList;
import java.util.List;

import com.minecolonies.api.colony.IColony;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import com.mrzeyner.colonynations.Config;
import com.mrzeyner.colonynations.command.Messages;
import com.mrzeyner.colonynations.data.NationSavedData;
import com.mrzeyner.colonynations.data.PendingNotice;
import com.mrzeyner.colonynations.economy.Separatism;
import com.mrzeyner.colonynations.nation.CityRef;
import com.mrzeyner.colonynations.nation.Nation;
import com.mrzeyner.colonynations.territory.ChunkRef;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Daily separatism step, and the operator command that sets a city's score.
 */
public final class SeparatismDay {
    private SeparatismDay() {
    }

    public static void advance(MinecraftServer server) {
        Secession.expire(server);
        NationSavedData data = NationSavedData.get(server);
        if (data.unreadable()) {
            return;
        }
        double threshold = Config.UNREST_THRESHOLD.get();
        int growth = Config.SEPARATISM_GROWTH.get();
        int decay = Config.SEPARATISM_DECAY.get();
        long gameTime = server.overworld().getGameTime();
        boolean dirty = false;
        for (Nation nation : List.copyOf(data.registry().all())) {
            for (CityRef city : nation.cities()) {
                IColony colony = MineColoniesCities.findByCity(city);
                if (colony == null) {
                    continue;
                }
                int before = nation.separatism(city);
                int after = Separatism.advance(
                        before,
                        colony.getOverallHappiness(),
                        threshold,
                        growth,
                        decay,
                        city.equals(nation.capital()));
                if (after == before) {
                    continue;
                }
                data.registry().setSeparatism(nation.id(), city, after);
                dirty = true;
                Nation updated = data.registry().byId(nation.id()).orElse(nation);
                announce(server, data, updated, colony, city, before, after, gameTime);
            }
        }
        if (dirty) {
            data.setDirty();
        }
    }

    public static int set(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        NationSavedData data = NationSavedData.get(source.getServer());
        if (data.refuseChanges(source)) {
            return 0;
        }
        int colonyId = IntegerArgumentType.getInteger(ctx, "colonyId");
        int value = IntegerArgumentType.getInteger(ctx, "value");
        ResourceKey<Level> prefer = source.getEntity() instanceof ServerPlayer player ? player.level().dimension() : null;
        Match match = find(data, colonyId, prefer);
        if (match == null) {
            return Messages.error(source, "command.colony_nations.city.unknown", colonyId);
        }
        int before = match.nation.separatism(match.city);
        Nation updated = data.registry().setSeparatism(match.nation.id(), match.city, value);
        data.setDirty();
        IColony colony = MineColoniesCities.findByCity(match.city);
        String label = colony == null || colony.getName() == null || colony.getName().isBlank()
                ? "#" + colonyId
                : colony.getName();
        if (colony != null) {
            refreshMap(source.getServer(), updated, colony, match.city, before, value, source.getServer().overworld().getGameTime());
        }
        Separatism.Crossing crossing = Separatism.crossing(before, value);
        if (crossing.unrest() && colony != null) {
            tellOwner(source.getServer(), data, updated, "command.colony_nations.separatism.unrest", label, updated.name());
        }
        if (crossing.movement() && colony != null) {
            tellEveryone(source.getServer(), data, updated, label);
        }
        if (crossing.independence()) {
            Secession.onIndependence(source.getServer(), updated, match.city, colony);
        }
        Nation still = data.registry().byId(match.nation.id()).orElse(null);
        if (still == null || !still.cities().contains(match.city)) {
            return 1;
        }
        return Messages.success(source, "command.colony_nations.separatism.set", label, colonyId, value);
    }

    public static String stageKey(int score) {
        return switch (Separatism.stage(score)) {
            case CALM -> "command.colony_nations.separatism.stage.calm";
            case UNREST -> "command.colony_nations.separatism.stage.unrest";
            case MOVEMENT -> "command.colony_nations.separatism.stage.movement";
            case INDEPENDENCE -> "command.colony_nations.separatism.stage.break";
        };
    }

    private static void announce(
            MinecraftServer server,
            NationSavedData data,
            Nation nation,
            IColony colony,
            CityRef city,
            int before,
            int after,
            long gameTime) {
        String label = colony.getName() == null || colony.getName().isBlank() ? "#" + city.colonyId() : colony.getName();
        Separatism.Crossing crossing = Separatism.crossing(before, after);
        if (crossing.unrest()) {
            tellOwner(server, data, nation, "command.colony_nations.separatism.unrest", label, nation.name());
        }
        if (crossing.movement()) {
            tellEveryone(server, data, nation, label);
        }
        if (crossing.independence()) {
            Secession.onIndependence(server, nation, city, colony);
        }
        Nation current = data.registry().byId(nation.id()).orElse(null);
        if (current != null && current.cities().contains(city) && Separatism.unrest(before) != Separatism.unrest(after)) {
            refreshMap(server, current, colony, city, before, after, gameTime);
        }
    }

    private static void refreshMap(
            MinecraftServer server,
            Nation nation,
            IColony colony,
            CityRef city,
            int before,
            int after,
            long gameTime) {
        if (Separatism.unrest(before) == Separatism.unrest(after)) {
            return;
        }
        String name = colony.getName() == null ? "" : colony.getName();
        TerritorySync.broadcastCity(
                server,
                nation,
                new ArrayList<ChunkRef>(ColonyClaimCache.get().chunks(colony, gameTime)),
                colony.getID(),
                name,
                Separatism.unrest(after));
    }

    private static void tellOwner(MinecraftServer server, NationSavedData data, Nation nation, String key, String city, String nationName) {
        ServerPlayer owner = server.getPlayerList().getPlayer(nation.owner());
        if (owner == null) {
            data.addNotice(new PendingNotice(nation.owner(), true, key, List.of(city, nationName)));
            return;
        }
        Messages.error(owner.createCommandSourceStack(), key, city, nationName);
    }

    private static void tellEveryone(MinecraftServer server, NationSavedData data, Nation nation, String city) {
        String key = "command.colony_nations.separatism.movement";
        boolean ownerOnline = false;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(nation.owner())) {
                ownerOnline = true;
            }
            Messages.event(player, key, city, nation.name());
        }
        if (!ownerOnline) {
            data.addNotice(new PendingNotice(nation.owner(), false, key, List.of(city, nation.name())));
        }
    }

    private static Match find(NationSavedData data, int colonyId, ResourceKey<Level> prefer) {
        List<Match> matches = new ArrayList<>();
        for (Nation nation : data.registry().all()) {
            for (CityRef city : nation.cities()) {
                if (city.colonyId() == colonyId) {
                    matches.add(new Match(nation, city));
                }
            }
        }
        if (matches.size() == 1) {
            return matches.get(0);
        }
        if (prefer == null) {
            return null;
        }
        String dimension = prefer.location().toString();
        Match found = null;
        for (Match match : matches) {
            if (!dimension.equals(match.city.dimension())) {
                continue;
            }
            if (found != null) {
                return null;
            }
            found = match;
        }
        return found;
    }

    private record Match(Nation nation, CityRef city) {
    }
}
