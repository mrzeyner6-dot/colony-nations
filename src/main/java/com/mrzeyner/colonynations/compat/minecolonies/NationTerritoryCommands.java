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
import com.minecolonies.api.colony.buildings.workerbuildings.ITownHall;

import com.mrzeyner.colonynations.Config;
import com.mrzeyner.colonynations.command.Messages;
import com.mrzeyner.colonynations.data.NationSavedData;
import com.mrzeyner.colonynations.nation.CityRef;
import com.mrzeyner.colonynations.nation.Nation;
import com.mrzeyner.colonynations.territory.AnnexMath;
import com.mrzeyner.colonynations.territory.ChunkDistance;
import com.mrzeyner.colonynations.territory.ChunkRef;
import com.mrzeyner.colonynations.territory.NearestCity;
import com.mrzeyner.colonynations.territory.TerritoryLink;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/**
 * /nation annex, unannex and territory. The owner must stand in the chunk being changed.
 */
public final class NationTerritoryCommands {
    private NationTerritoryCommands() {
    }

    public static int annex(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        NationSavedData data = NationSavedData.get(source.getServer());
        if (data.refuseChanges(source)) {
            return 0;
        }
        ServerPlayer player = player(source);
        if (player == null) {
            return Messages.error(source, "command.colony_nations.players_only");
        }
        Nation nation = data.registry().byOwner(player.getUUID()).orElse(null);
        if (nation == null) {
            return Messages.error(source, "command.colony_nations.info.none");
        }
        ChunkRef here = standing(player);
        Optional<UUID> owner = data.territory().owner(here);
        if (owner.isPresent()) {
            String key = owner.get().equals(nation.id())
                    ? "command.colony_nations.annex.yours"
                    : "command.colony_nations.annex.foreign";
            return Messages.error(source, key);
        }
        if (ColonyClaims.isCityLand(player.level().dimension(), here)) {
            return Messages.error(source, "command.colony_nations.annex.city");
        }
        Survey survey = survey(nation, source.getLevel().getGameTime());
        Set<ChunkRef> territory = new HashSet<>(data.territory().chunks(nation.id()));
        territory.addAll(survey.cities());
        if (!TerritoryLink.touches(here, territory)) {
            return Messages.error(source, "command.colony_nations.annex.not_adjacent");
        }
        Optional<CityRef> nearest = NearestCity.find(here, survey.anchors());
        if (nearest.isEmpty()) {
            return Messages.error(source, "command.colony_nations.annex.no_city");
        }
        NearestCity.Anchor anchor = survey.anchors().stream()
                .filter(candidate -> candidate.city().equals(nearest.get()))
                .findFirst()
                .orElseThrow();
        long distance = ChunkDistance.rounded(here.x(), here.z(), anchor.centerChunkX(), anchor.centerChunkZ());
        long price = AnnexMath.price(Config.ANNEX_BASE_COST.get(), Config.ANNEX_COST_PER_CHUNK_DISTANCE.get(), distance);
        int limit = AnnexMath.limit(
                Config.ADMIN_BASE.get(),
                Config.ADMIN_PER_CITY.get(),
                nation.cities().size(),
                Config.ADMIN_PER_TOWN_HALL_LEVEL.get(),
                survey.townHallLevels());
        if (data.territory().count(nation.id()) >= limit) {
            return Messages.error(source, "command.colony_nations.annex.limit", limit);
        }
        if (nation.treasury() < price) {
            return Messages.error(source, "command.colony_nations.annex.poor", price, nation.treasury());
        }
        data.territory().claim(here, nation.id());
        long updated = nation.treasury() - price;
        data.registry().setTreasury(nation.id(), updated);
        data.setDirty();
        TerritorySync.stateClaim(source.getServer(), here, nation);
        return Messages.success(source, "command.colony_nations.annex.success", price, updated);
    }

    public static int unannex(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        NationSavedData data = NationSavedData.get(source.getServer());
        if (data.refuseChanges(source)) {
            return 0;
        }
        ServerPlayer player = player(source);
        if (player == null) {
            return Messages.error(source, "command.colony_nations.players_only");
        }
        Nation nation = data.registry().byOwner(player.getUUID()).orElse(null);
        if (nation == null) {
            return Messages.error(source, "command.colony_nations.info.none");
        }
        ChunkRef here = standing(player);
        if (!nation.id().equals(data.territory().owner(here).orElse(null))) {
            return Messages.error(source, "command.colony_nations.unannex.not_yours");
        }
        Survey survey = survey(nation, source.getLevel().getGameTime());
        if (!TerritoryLink.staysConnected(data.territory().chunks(nation.id()), survey.cities(), here)) {
            return Messages.error(source, "command.colony_nations.unannex.disconnects");
        }
        data.territory().release(here);
        data.setDirty();
        TerritorySync.release(source.getServer(), here);
        return Messages.success(source, "command.colony_nations.unannex.success");
    }

    public static int territory(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
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
        Survey survey = survey(nation, source.getLevel().getGameTime());
        int state = data.territory().count(nation.id());
        int limit = AnnexMath.limit(
                Config.ADMIN_BASE.get(),
                Config.ADMIN_PER_CITY.get(),
                nation.cities().size(),
                Config.ADMIN_PER_TOWN_HALL_LEVEL.get(),
                survey.townHallLevels());
        long upkeep = (long) state * Config.UPKEEP_PER_CHUNK.get();
        Messages.header(source, "command.colony_nations.territory.header", nation.name());
        Messages.line(source, "command.colony_nations.territory.state", state);
        Messages.line(source, "command.colony_nations.territory.city", survey.cities().size());
        Messages.line(source, "command.colony_nations.territory.limit", limit);
        Messages.line(source, "command.colony_nations.territory.upkeep", upkeep);
        return 1;
    }

    private static Survey survey(Nation nation, long gameTime) {
        Set<ChunkRef> cities = new HashSet<>();
        List<NearestCity.Anchor> anchors = new ArrayList<>();
        int levels = 0;
        for (CityRef city : nation.cities()) {
            IColony colony = MineColoniesCities.findByCity(city);
            if (colony == null) {
                continue;
            }
            cities.addAll(ColonyClaimCache.get().chunks(colony, gameTime));
            BlockPos center = colony.getCenter();
            anchors.add(new NearestCity.Anchor(city, center.getX() >> 4, center.getZ() >> 4));
            ITownHall hall = colony.getServerBuildingManager().getTownHall();
            if (hall != null) {
                levels += hall.getBuildingLevel();
            }
        }
        return new Survey(cities, anchors, levels);
    }

    private static ChunkRef standing(ServerPlayer player) {
        BlockPos pos = player.blockPosition();
        return new ChunkRef(player.level().dimension().location().toString(), pos.getX() >> 4, pos.getZ() >> 4);
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

    private record Survey(Set<ChunkRef> cities, List<NearestCity.Anchor> anchors, int townHallLevels) {
    }
}
