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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

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

    public static int set(CommandContext<CommandSourceStack> ctx, String raw) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        int value = IntegerArgumentType.getInteger(ctx, "value");
        CityArguments.Hit hit = CityArguments.resolve(source, raw, CityArguments.Pool.MEMBER);
        if (hit == null || hit.nation() == null) {
            return 0;
        }
        NationSavedData data = NationSavedData.get(source.getServer());
        int colonyId = hit.colonyId();
        int before = hit.nation().separatism(hit.city());
        Nation updated = data.registry().setSeparatism(hit.nation().id(), hit.city(), value);
        data.setDirty();
        IColony colony = hit.colony() != null ? hit.colony() : MineColoniesCities.findByCity(hit.city());
        String label = hit.label();
        if (colony != null) {
            refreshMap(source.getServer(), updated, colony, hit.city(), before, value, source.getServer().overworld().getGameTime());
        }
        Separatism.Crossing crossing = Separatism.crossing(before, value);
        if (crossing.unrest() && colony != null) {
            tellOwner(source.getServer(), data, updated, "command.colony_nations.separatism.unrest", label, updated.name());
        }
        if (crossing.movement() && colony != null) {
            tellEveryone(source.getServer(), data, updated, label);
        }
        if (crossing.independence()) {
            Secession.onIndependence(source.getServer(), updated, hit.city(), colony);
        }
        Nation still = data.registry().byId(hit.nation().id()).orElse(null);
        if (still == null || !still.cities().contains(hit.city())) {
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
        if (current != null && current.cities().contains(city) && Separatism.mapBand(before) != Separatism.mapBand(after)) {
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
        if (Separatism.mapBand(before) == Separatism.mapBand(after)) {
            return;
        }
        String name = colony.getName() == null ? "" : colony.getName();
        TerritorySync.broadcastCity(
                server,
                nation,
                new ArrayList<ChunkRef>(ColonyClaimCache.get().chunks(colony, gameTime)),
                colony.getID(),
                name,
                Separatism.mapBand(after));
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
}
