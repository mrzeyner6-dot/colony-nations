package com.mrzeyner.colonynations.compat.minecolonies;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import com.minecolonies.api.colony.IColony;

import com.mrzeyner.colonynations.Config;
import com.mrzeyner.colonynations.command.Messages;
import com.mrzeyner.colonynations.data.NationSavedData;
import com.mrzeyner.colonynations.network.TerritorySync;
import com.mrzeyner.colonynations.nation.CityRef;
import com.mrzeyner.colonynations.nation.Nation;
import com.mrzeyner.colonynations.territory.AdminLimit;
import com.mrzeyner.colonynations.territory.AnnexCost;
import com.mrzeyner.colonynations.territory.ChunkRef;
import com.mrzeyner.colonynations.territory.CityCenter;
import com.mrzeyner.colonynations.territory.NationReach;
import com.mrzeyner.colonynations.territory.NearestCity;
import com.mrzeyner.colonynations.territory.TerritoryLinks;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/**
 * /nation annex, /nation unannex and /nation territory. The owner must be standing in the chunk.
 */
public final class NationTerritoryCommands {
    private NationTerritoryCommands() {
    }

    public static int annex(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
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
        ChunkRef chunk = standing(player);
        if (ColonyClaimChunks.isNationCityLand(data.registry(), player.level().dimension(), player.chunkPosition())) {
            return Messages.error(source, "command.colony_nations.annex.city");
        }
        Optional<UUID> owner = data.territory().owner(chunk);
        if (owner.isPresent()) {
            String name = data.registry().byId(owner.get()).map(Nation::name).orElse("?");
            return Messages.error(source, "command.colony_nations.annex.taken", name);
        }
        Set<ChunkRef> cityChunks = ColonyClaimChunks.ofNation(nation);
        Set<ChunkRef> nationLand = new HashSet<>(data.territory().chunks(nation.id()));
        nationLand.addAll(cityChunks);
        if (!NationReach.touches(chunk, nationLand)) {
            return Messages.error(source, "command.colony_nations.annex.not_adjacent");
        }
        List<CityCenter> centers = centers(nation);
        CityRef nearest = NearestCity.find(chunk, centers).orElse(null);
        if (nearest == null) {
            return Messages.error(source, "command.colony_nations.annex.no_city");
        }
        ChunkRef center = center(centers, nearest);
        int distance = NearestCity.chunkDistance(chunk, center);
        long price = AnnexCost.price(
                distance,
                Config.ANNEX_BASE_COST.get(),
                Config.ANNEX_COST_PER_CHUNK_DISTANCE.get());
        long limit = limit(nation);
        if (data.territory().count(nation.id()) + 1L > limit) {
            return Messages.error(source, "command.colony_nations.annex.limit", limit);
        }
        if (nation.treasury() < price) {
            return Messages.error(source, "command.colony_nations.annex.afford", nation.name(), price, nation.treasury());
        }
        Set<ChunkRef> before = data.territory().chunks(nation.id());
        data.registry().setTreasury(nation.id(), nation.treasury() - price);
        data.territory().claim(chunk, nation.id());
        data.setDirty();
        TerritorySync.stateChanged(source.getServer(), nation.id(), before, data.territory().chunks(nation.id()));
        return Messages.success(
                source,
                "command.colony_nations.annex.success",
                chunk.x(),
                chunk.z(),
                chunk.dimension(),
                price,
                nation.treasury() - price);
    }

    public static int unannex(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
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
        ChunkRef chunk = standing(player);
        if (!nation.id().equals(data.territory().owner(chunk).orElse(null))) {
            return Messages.error(source, "command.colony_nations.unannex.not_ours", nation.name());
        }
        Set<ChunkRef> cities = ColonyClaimChunks.ofNation(nation);
        Set<ChunkRef> before = data.territory().chunks(nation.id());
        if (!TerritoryLinks.staysConnected(before, cities, chunk)) {
            return Messages.error(source, "command.colony_nations.unannex.cut");
        }
        data.territory().release(chunk);
        data.setDirty();
        TerritorySync.stateChanged(source.getServer(), nation.id(), before, data.territory().chunks(nation.id()));
        return Messages.success(source, "command.colony_nations.unannex.success", chunk.x(), chunk.z());
    }

    public static int show(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = player(source);
        if (player == null) {
            return Messages.error(source, "command.colony_nations.players_only");
        }
        Nation nation = NationSavedData.get(source.getServer()).registry().byOwner(player.getUUID()).orElse(null);
        if (nation == null) {
            return Messages.error(source, "command.colony_nations.info.none");
        }
        NationSavedData data = NationSavedData.get(source.getServer());
        int state = data.territory().count(nation.id());
        int cities = ColonyClaimChunks.ofNation(nation).size();
        long limit = limit(nation);
        long upkeep = (long) state * Config.UPKEEP_PER_CHUNK.get();
        Messages.header(source, "command.colony_nations.territory.header");
        Messages.line(source, "command.colony_nations.territory.state", state);
        Messages.line(source, "command.colony_nations.territory.city", cities);
        Messages.line(source, "command.colony_nations.territory.limit", limit);
        Messages.line(source, "command.colony_nations.territory.upkeep", upkeep);
        return 1;
    }

    private static long limit(Nation nation) {
        return AdminLimit.chunks(
                Config.ADMIN_BASE.get(),
                Config.ADMIN_PER_CITY.get(),
                nation.cities().size(),
                Config.ADMIN_PER_TOWN_HALL_LEVEL.get(),
                ColonyClaimChunks.townHallLevels(nation));
    }

    private static List<CityCenter> centers(Nation nation) {
        List<CityCenter> centers = new ArrayList<>();
        for (CityRef city : nation.cities()) {
            IColony colony = MineColoniesCities.findByCity(city);
            if (colony == null || colony.getCenter() == null) {
                continue;
            }
            BlockPos pos = colony.getCenter();
            centers.add(new CityCenter(city, new ChunkRef(city.dimension(), pos.getX() >> 4, pos.getZ() >> 4)));
        }
        return centers;
    }

    private static ChunkRef center(List<CityCenter> centers, CityRef city) {
        for (CityCenter center : centers) {
            if (center.city().equals(city)) {
                return center.center();
            }
        }
        throw new IllegalStateException("nearest city has no center");
    }

    private static ChunkRef standing(ServerPlayer player) {
        return new ChunkRef(player.level().dimension().location().toString(), player.chunkPosition().x, player.chunkPosition().z);
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
}
