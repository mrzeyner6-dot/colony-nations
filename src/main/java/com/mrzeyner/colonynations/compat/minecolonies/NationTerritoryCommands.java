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
import com.mrzeyner.colonynations.command.NationOperations;
import com.mrzeyner.colonynations.data.NationSavedData;
import com.mrzeyner.colonynations.nation.CityRef;
import com.mrzeyner.colonynations.nation.Nation;
import com.mrzeyner.colonynations.territory.AnnexMath;
import com.mrzeyner.colonynations.territory.ChunkDistance;
import com.mrzeyner.colonynations.territory.ChunkRef;
import com.mrzeyner.colonynations.territory.CityCore;
import com.mrzeyner.colonynations.territory.NearestCity;
import com.mrzeyner.colonynations.territory.TerritoryLink;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/**
 * /nation annex, unannex and territory. The owner must stand in the chunk being changed.
 * {@link Preview} is the same check the menu shows next to its buttons.
 */
public final class NationTerritoryCommands {
    private NationTerritoryCommands() {
    }

    /**
     * Why the chunk under the player can or cannot be annexed or released, plus the land totals.
     * Annex codes and release codes share {@link Preview#OK} and otherwise use their own constants.
     */
    public record Preview(
            int cityChunks,
            int coreChunks,
            int annexed,
            int limit,
            long upkeep,
            int annexCode,
            long annexPrice,
            int unannexCode) {
        public static final int OK = 0;
        public static final int YOURS = 1;
        public static final int FOREIGN = 2;
        public static final int CITY_LAND = 3;
        public static final int NOT_ADJACENT = 4;
        public static final int NO_CITY = 5;
        public static final int LIMIT = 6;
        public static final int POOR = 7;
        public static final int NOT_YOURS = 1;
        public static final int CORE = 2;
        public static final int DISCONNECTS = 3;
    }

    public static int annex(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return annex(ctx.getSource());
    }

    public static int annex(CommandSourceStack source) throws CommandSyntaxException {
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
        Preview preview = preview(player, nation, data, source.getLevel().getGameTime());
        return switch (preview.annexCode()) {
            case Preview.YOURS -> Messages.error(source, "command.colony_nations.annex.yours");
            case Preview.FOREIGN -> Messages.error(source, "command.colony_nations.annex.foreign");
            case Preview.CITY_LAND -> Messages.error(source, "command.colony_nations.annex.city");
            case Preview.NOT_ADJACENT -> Messages.error(source, "command.colony_nations.annex.not_adjacent");
            case Preview.NO_CITY -> Messages.error(source, "command.colony_nations.annex.no_city");
            case Preview.LIMIT -> Messages.error(source, "command.colony_nations.annex.limit", preview.limit());
            case Preview.POOR -> Messages.error(source, "command.colony_nations.annex.poor", preview.annexPrice(), nation.treasury());
            case Preview.OK -> claim(source, data, player, nation, preview.annexPrice());
            default -> 0;
        };
    }

    public static int unannex(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return unannex(ctx.getSource());
    }

    public static int unannex(CommandSourceStack source) throws CommandSyntaxException {
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
        Preview preview = preview(player, nation, data, source.getLevel().getGameTime());
        return switch (preview.unannexCode()) {
            case Preview.NOT_YOURS -> Messages.error(source, "command.colony_nations.unannex.not_yours");
            case Preview.CORE -> Messages.error(source, "command.colony_nations.unannex.core");
            case Preview.DISCONNECTS -> Messages.error(source, "command.colony_nations.unannex.disconnects");
            case Preview.OK -> release(source, data, player);
            default -> 0;
        };
    }

    public static int territory(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = player(source);
        if (player == null) {
            return Messages.error(source, "command.colony_nations.players_only");
        }
        NationSavedData data = NationSavedData.get(source.getServer());
        Nation nation = data.registry().byOwner(player.getUUID()).orElse(null);
        if (nation == null) {
            return Messages.error(source, "command.colony_nations.info.none");
        }
        Preview preview = preview(player, nation, data, source.getLevel().getGameTime());
        Messages.header(source, "command.colony_nations.territory.header", nation.name());
        Messages.line(source, "command.colony_nations.territory.city", preview.cityChunks());
        Messages.line(source, "command.colony_nations.territory.core", preview.coreChunks());
        Messages.line(source, "command.colony_nations.territory.annexed", preview.annexed(), preview.limit());
        Messages.line(source, "command.colony_nations.territory.upkeep", preview.upkeep());
        return 1;
    }

    public static Preview preview(ServerPlayer player, Nation nation, NationSavedData data, long gameTime) {
        ChunkRef here = standing(player);
        Survey survey = survey(nation, gameTime);
        Set<ChunkRef> state = data.territory().chunks(nation.id());
        int width = Config.FOUNDING_RING_CHUNKS.get();
        int core = CityCore.among(state, List.of(survey.cities()), width).size();
        int annexed = state.size() - core;
        int limit = AnnexMath.limit(
                Config.ADMIN_BASE.get(),
                Config.ADMIN_PER_CITY.get(),
                nation.cities().size(),
                Config.ADMIN_PER_TOWN_HALL_LEVEL.get(),
                survey.townHallLevels());
        long upkeep = (long) annexed * Config.UPKEEP_PER_CHUNK.get();
        int annexCode = Preview.OK;
        long price = 0L;
        Optional<UUID> owner = data.territory().owner(here);
        if (owner.isPresent()) {
            annexCode = owner.get().equals(nation.id()) ? Preview.YOURS : Preview.FOREIGN;
        } else if (ColonyClaims.isCityLand(player.level().dimension(), here)) {
            annexCode = Preview.CITY_LAND;
        } else {
            Set<ChunkRef> territory = new HashSet<>(state);
            territory.addAll(survey.cities());
            if (!TerritoryLink.touches(here, territory)) {
                annexCode = Preview.NOT_ADJACENT;
            } else {
                Optional<CityRef> nearest = NearestCity.find(here, survey.anchors());
                if (nearest.isEmpty()) {
                    annexCode = Preview.NO_CITY;
                } else {
                    NearestCity.Anchor anchor = survey.anchors().stream()
                            .filter(candidate -> candidate.city().equals(nearest.get()))
                            .findFirst()
                            .orElseThrow();
                    long distance = ChunkDistance.rounded(here.x(), here.z(), anchor.centerChunkX(), anchor.centerChunkZ());
                    price = AnnexMath.price(Config.ANNEX_BASE_COST.get(), Config.ANNEX_COST_PER_CHUNK_DISTANCE.get(), distance);
                    boolean joiningCore = !CityCore.among(Set.of(here), List.of(survey.cities()), width).isEmpty();
                    if (!joiningCore && annexed >= limit) {
                        annexCode = Preview.LIMIT;
                    } else if (nation.treasury() < price) {
                        annexCode = Preview.POOR;
                    }
                }
            }
        }
        int unannexCode = Preview.OK;
        if (!nation.id().equals(data.territory().owner(here).orElse(null))) {
            unannexCode = Preview.NOT_YOURS;
        } else if (CityCore.among(Set.of(here), List.of(survey.cities()), width).contains(here)) {
            unannexCode = Preview.CORE;
        } else if (!TerritoryLink.staysConnected(state, survey.cities(), here)) {
            unannexCode = Preview.DISCONNECTS;
        }
        return new Preview(survey.cities().size(), core, annexed, limit, upkeep, annexCode, price, unannexCode);
    }

    private static int claim(CommandSourceStack source, NationSavedData data, ServerPlayer player, Nation nation, long price) {
        ChunkRef here = standing(player);
        data.territory().claim(here, nation.id());
        long updated = nation.treasury() - price;
        data.registry().setTreasury(nation.id(), updated);
        data.setDirty();
        TerritorySync.stateClaim(source.getServer(), here, nation);
        return Messages.success(source, "command.colony_nations.annex.success", price, updated);
    }

    private static int release(CommandSourceStack source, NationSavedData data, ServerPlayer player) {
        ChunkRef here = standing(player);
        data.territory().release(here);
        data.setDirty();
        TerritorySync.release(source.getServer(), here);
        return Messages.success(source, "command.colony_nations.unannex.success");
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
        return NationOperations.player(source);
    }

    private record Survey(Set<ChunkRef> cities, List<NearestCity.Anchor> anchors, int townHallLevels) {
    }
}
