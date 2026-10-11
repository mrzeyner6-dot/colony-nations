package com.mrzeyner.colonynations.compat.minecolonies;

import java.util.HashSet;
import java.util.Set;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import com.minecolonies.api.colony.IColony;

import com.mrzeyner.colonynations.Config;
import com.mrzeyner.colonynations.command.Messages;
import com.mrzeyner.colonynations.command.NationOperations;
import com.mrzeyner.colonynations.data.FreeCity;
import com.mrzeyner.colonynations.data.NationSavedData;
import com.mrzeyner.colonynations.economy.FreeCityBuy;
import com.mrzeyner.colonynations.economy.Separatism;
import com.mrzeyner.colonynations.nation.CityRef;
import com.mrzeyner.colonynations.nation.Nation;
import com.mrzeyner.colonynations.nation.NationException;
import com.mrzeyner.colonynations.territory.ChunkRef;
import com.mrzeyner.colonynations.territory.SecessionLand;
import com.mrzeyner.colonynations.territory.Upkeep;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Buys a free city into the owner's nation. The founding ring is not annexed.
 * {@code /nation admin city give} uses the same join without the price, range or cooldown.
 */
public final class FreeCityJoin {
    private FreeCityJoin() {
    }

    public static int buy(CommandSourceStack source, String raw) throws CommandSyntaxException {
        NationSavedData data = NationSavedData.get(source.getServer());
        if (data.refuseChanges(source)) {
            return 0;
        }
        ServerPlayer player = NationOperations.player(source);
        if (player == null) {
            return Messages.error(source, "command.colony_nations.players_only");
        }
        Nation nation = data.registry().byOwner(player.getUUID()).orElse(null);
        if (nation == null) {
            return Messages.error(source, "command.colony_nations.info.none");
        }
        CityArguments.Hit hit = CityArguments.resolve(source, raw, CityArguments.Pool.FREE);
        if (hit == null) {
            return 0;
        }
        return finish(source, data, nation, player, find(data, hit.city()), hit.label());
    }

    public static int buyAt(CommandSourceStack source, String dimension, int colonyId) throws CommandSyntaxException {
        NationSavedData data = NationSavedData.get(source.getServer());
        if (data.refuseChanges(source)) {
            return 0;
        }
        ServerPlayer player = NationOperations.player(source);
        if (player == null) {
            return Messages.error(source, "command.colony_nations.players_only");
        }
        Nation nation = data.registry().byOwner(player.getUUID()).orElse(null);
        if (nation == null) {
            return Messages.error(source, "command.colony_nations.info.none");
        }
        CityRef city = new CityRef(dimension == null ? "" : dimension, colonyId);
        FreeCity free = find(data, city);
        return finish(source, data, nation, player, free, free == null ? "#" + colonyId : free.name());
    }

    private static int finish(
            CommandSourceStack source,
            NationSavedData data,
            Nation nation,
            ServerPlayer player,
            FreeCity free,
            String label) {
        if (free == null) {
            return Messages.error(source, "command.colony_nations.freecity.not_free", label);
        }
        long gameTime = source.getLevel().getGameTime();
        long today = day(source.getServer());
        FreeCityBuy.Quote quote = quote(data, nation, free, gameTime, today);
        if (quote.block() != FreeCityBuy.OK) {
            return blocked(source, free.name().isBlank() ? label : free.name(), nation.treasury(), quote);
        }
        IColony colony = MineColoniesCities.findByCity(free.city());
        return join(source, data, nation, free, colony, player, quote.price(), gameTime, today);
    }

    public static int give(CommandSourceStack source, String raw, String nationName) throws CommandSyntaxException {
        NationSavedData data = NationSavedData.get(source.getServer());
        if (data.refuseChanges(source)) {
            return 0;
        }
        Nation nation = data.registry().byName(nationName).orElse(null);
        if (nation == null) {
            return Messages.error(source, "command.colony_nations.unknown", nationName);
        }
        ServerPlayer owner = source.getServer().getPlayerList().getPlayer(nation.owner());
        if (owner == null) {
            return Messages.error(source, "command.colony_nations.freecity.owner_offline", nation.name());
        }
        CityArguments.Hit hit = CityArguments.resolve(source, raw, CityArguments.Pool.FREE);
        if (hit == null) {
            return 0;
        }
        FreeCity free = find(data, hit.city());
        if (free == null) {
            return Messages.error(source, "command.colony_nations.freecity.not_free", hit.label());
        }
        IColony colony = hit.colony() != null ? hit.colony() : MineColoniesCities.findByCity(free.city());
        return join(source, data, nation, free, colony, owner, -1L, source.getLevel().getGameTime(), day(source.getServer()));
    }

    public static FreeCityBuy.Quote quote(NationSavedData data, Nation nation, FreeCity free, long gameTime, long today) {
        IColony colony = MineColoniesCities.findByCity(free.city());
        Set<ChunkRef> claim = colony == null ? Set.of() : ColonyClaimCache.get().chunks(colony, gameTime);
        int distance = SecessionLand.nearest(claim, landOf(data, nation, gameTime));
        boolean returning = nation.id().equals(free.formerNation());
        return FreeCityBuy.quote(
                Config.FREE_CITY_BASE_COST.get(),
                Config.FREE_CITY_COST_PER_CITIZEN.get(),
                NationTaxes.adults(colony),
                returning,
                Config.FREE_CITY_RETURN_MULTIPLIER.get(),
                distance,
                Config.SECESSION_JOIN_RADIUS.get(),
                nation.treasury(),
                data.freeJoinDay(free.city()),
                today,
                Config.FREE_CITY_JOIN_COOLDOWN_DAYS.get());
    }

    private static int join(
            CommandSourceStack source,
            NationSavedData data,
            Nation nation,
            FreeCity free,
            IColony colony,
            ServerPlayer owner,
            long price,
            long gameTime,
            long today) {
        if (colony == null) {
            data.removeFreeCity(free.city());
            return Messages.error(source, "command.colony_nations.city.unknown", free.city().colonyId());
        }
        MinecraftServer server = source.getServer();
        Set<ChunkRef> claim = new HashSet<>(ColonyClaimCache.get().chunks(colony, gameTime));
        try {
            data.registry().addColony(nation.id(), free.city());
        } catch (NationException exception) {
            return Messages.error(source, "command.colony_nations.city.taken", free.city().colonyId());
        }
        Nation joined = data.registry().setSeparatism(
                nation.id(), free.city(), Config.FREE_CITY_JOIN_SEPARATISM.get());
        if (price >= 0L) {
            data.registry().setTreasury(joined.id(), joined.treasury() - price);
            joined = data.registry().byId(joined.id()).orElse(joined);
            data.setFreeJoinDay(free.city(), today);
        }
        CityTerritory.releaseClaim(data, claim);
        data.removeFreeCity(free.city());
        colony.getPermissions().setOwner(owner);
        CitizenTaxHappiness.clear(colony);
        CitizenTaxHappiness.apply(joined);
        data.setDirty();
        String name = free.name().isBlank() ? colony.getName() : free.name();
        TerritorySync.broadcastCity(
                server,
                joined,
                claim,
                colony.getID(),
                name,
                Separatism.mapBand(joined.separatism(free.city())));
        TerritorySync.forgetNation(server, FreeCity.mapId(free.city()));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Messages.event(player, "command.colony_nations.freecity.joined", joined.name(), name);
        }
        return 1;
    }

    private static int blocked(CommandSourceStack source, String name, long treasury, FreeCityBuy.Quote quote) {
        return switch (quote.block()) {
            case FreeCityBuy.TOO_FAR -> Messages.error(source, "command.colony_nations.freecity.far", name);
            case FreeCityBuy.COOLDOWN -> Messages.error(source, "command.colony_nations.freecity.cooldown", name, quote.readyDay());
            case FreeCityBuy.POOR -> Messages.error(source, "command.colony_nations.freecity.poor", name, quote.price(), treasury);
            default -> 0;
        };
    }

    private static Set<ChunkRef> landOf(NationSavedData data, Nation nation, long gameTime) {
        Set<ChunkRef> land = new HashSet<>(data.territory().chunks(nation.id()));
        for (CityRef city : nation.cities()) {
            IColony colony = MineColoniesCities.findByCity(city);
            if (colony != null) {
                land.addAll(ColonyClaimCache.get().chunks(colony, gameTime));
            }
        }
        return land;
    }

    private static FreeCity find(NationSavedData data, CityRef city) {
        for (FreeCity free : data.freeCities()) {
            if (free.city().equals(city)) {
                return free;
            }
        }
        return null;
    }

    private static long day(MinecraftServer server) {
        return server.overworld().getDayTime() / Upkeep.TICKS_PER_DAY;
    }
}
