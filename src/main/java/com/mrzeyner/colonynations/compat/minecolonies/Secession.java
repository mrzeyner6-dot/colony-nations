package com.mrzeyner.colonynations.compat.minecolonies;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.minecolonies.api.colony.IColony;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import com.mrzeyner.colonynations.ColonyNations;
import com.mrzeyner.colonynations.Config;
import com.mrzeyner.colonynations.command.Messages;
import com.mrzeyner.colonynations.data.FreeCity;
import com.mrzeyner.colonynations.data.NationSavedData;
import com.mrzeyner.colonynations.data.PendingNotice;
import com.mrzeyner.colonynations.data.SecessionOffer;
import com.mrzeyner.colonynations.economy.SecessionJoin;
import com.mrzeyner.colonynations.economy.Separatism;
import com.mrzeyner.colonynations.nation.CityRef;
import com.mrzeyner.colonynations.nation.Nation;
import com.mrzeyner.colonynations.territory.ChunkRef;
import com.mrzeyner.colonynations.territory.NearestCity;
import com.mrzeyner.colonynations.territory.SecessionLand;
import com.mrzeyner.colonynations.territory.Upkeep;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * A city at separatism 100 asks a lower-tax neighbour to take it in, or becomes a free city.
 * The capital never leaves. An offer lasts until the next overworld day.
 */
public final class Secession {
    private static final String OFFER = "command.colony_nations.secession.offer";
    private static final String JOINED = "command.colony_nations.secession.joined";
    private static final String FREE = "command.colony_nations.secession.free";

    private Secession() {
    }

    /**
     * Offers that were made on an earlier day become independence. A same-day offer stays.
     * Called at the start of the daily separatism step.
     */
    public static void expire(MinecraftServer server) {
        NationSavedData data = NationSavedData.get(server);
        if (data.unreadable()) {
            return;
        }
        long today = day(server);
        List<SecessionOffer> due = new ArrayList<>();
        for (SecessionOffer offer : data.offers()) {
            if (today > offer.offerDay()) {
                due.add(offer);
            }
        }
        for (SecessionOffer offer : due) {
            data.takeOffer(offer.city());
            Nation from = data.registry().byId(offer.fromNation()).orElse(null);
            if (from == null || !from.cities().contains(offer.city())) {
                continue;
            }
            becomeFree(server, data, from, offer.city(), MineColoniesCities.findByCity(offer.city()));
        }
    }

    /**
     * Daily or operator crossing to 100. The capital is left in place. A city that already has an offer is left alone.
     */
    public static void onIndependence(MinecraftServer server, Nation nation, CityRef city, IColony colony) {
        if (nation == null || city == null || city.equals(nation.capital())) {
            return;
        }
        NationSavedData data = NationSavedData.get(server);
        if (data.unreadable() || pending(data, city)) {
            return;
        }
        Nation current = data.registry().byId(nation.id()).orElse(nation);
        if (!current.cities().contains(city)) {
            return;
        }
        breakAway(server, data, current, city, colony == null ? MineColoniesCities.findByCity(city) : colony);
    }

    public static void onColonyDeleted(MinecraftServer server, NationSavedData data, CityRef city) {
        data.takeOffer(city);
        boolean free = false;
        for (FreeCity existing : data.freeCities()) {
            if (existing.city().equals(city)) {
                free = true;
                break;
            }
        }
        if (!free) {
            return;
        }
        data.removeFreeCity(city);
        TerritorySync.forgetNation(server, FreeCity.mapId(city));
    }

    public static void dropOffer(NationSavedData data, CityRef city) {
        data.takeOffer(city);
    }

    public static void dropNation(NationSavedData data, UUID nationId) {
        List<CityRef> drop = new ArrayList<>();
        for (SecessionOffer offer : data.offers()) {
            if (nationId.equals(offer.fromNation()) || nationId.equals(offer.toNation())) {
                drop.add(offer.city());
            }
        }
        for (CityRef city : drop) {
            data.takeOffer(city);
        }
    }

    public static int accept(CommandContext<CommandSourceStack> ctx, String raw) throws CommandSyntaxException {
        return answer(ctx, raw, true);
    }

    public static int decline(CommandContext<CommandSourceStack> ctx, String raw) throws CommandSyntaxException {
        return answer(ctx, raw, false);
    }

    public static boolean replayOffer(CommandSourceStack source, List<String> args) {
        if (args.size() < 3) {
            return false;
        }
        String id = args.get(2);
        Messages.info(source, OFFER, args.get(0), args.get(1), acceptButton(id), declineButton(id));
        return true;
    }

    public static int force(CommandContext<CommandSourceStack> ctx, String raw) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        CityArguments.Hit hit = CityArguments.resolve(source, raw, CityArguments.Pool.MEMBER);
        if (hit == null || hit.nation() == null) {
            return 0;
        }
        NationSavedData data = NationSavedData.get(source.getServer());
        if (hit.city().equals(hit.nation().capital())) {
            return Messages.error(source, "command.colony_nations.secession.capital");
        }
        if (pending(data, hit.city())) {
            return Messages.error(source, "command.colony_nations.secession.pending", hit.colonyId());
        }
        IColony colony = hit.colony() != null ? hit.colony() : MineColoniesCities.findByCity(hit.city());
        data.registry().setSeparatism(hit.nation().id(), hit.city(), Separatism.MAX);
        data.setDirty();
        Nation current = data.registry().byId(hit.nation().id()).orElse(hit.nation());
        boolean offered = breakAway(source.getServer(), data, current, hit.city(), colony);
        if (offered || !(source.getEntity() instanceof ServerPlayer)) {
            return Messages.success(source, "command.colony_nations.secession.forced", hit.label(), hit.colonyId());
        }
        return 1;
    }

    /**
     * @return true when the city stayed and asked another nation to take it
     */
    private static boolean breakAway(
            MinecraftServer server,
            NationSavedData data,
            Nation nation,
            CityRef city,
            IColony colony) {
        long gameTime = server.overworld().getGameTime();
        Set<ChunkRef> claim = claimOf(colony, gameTime);
        List<SecessionJoin.Candidate> neighbors = neighbors(data, nation, claim, gameTime);
        SecessionJoin.Candidate chosen = SecessionJoin.choose(
                nation.taxRate(), Config.SECESSION_JOIN_RADIUS.get(), neighbors).orElse(null);
        Nation target = chosen == null ? null : data.registry().byId(chosen.nationId()).orElse(null);
        if (target != null) {
            String name = label(colony, city);
            data.addOffer(new SecessionOffer(city, nation.id(), target.id(), name, day(server)));
            tellOffer(server, data, target, name, city.colonyId());
            return true;
        }
        becomeFree(server, data, nation, city, colony);
        return false;
    }

    private static int answer(CommandContext<CommandSourceStack> ctx, String raw, boolean accept) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getEntity() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        if (player == null) {
            return Messages.error(source, "command.colony_nations.players_only");
        }
        CityArguments.Hit hit = CityArguments.resolve(source, raw, CityArguments.Pool.OFFER);
        if (hit == null) {
            return 0;
        }
        NationSavedData data = NationSavedData.get(source.getServer());
        Nation mine = hit.nation();
        SecessionOffer offer = null;
        for (SecessionOffer candidate : data.offers()) {
            if (candidate.city().equals(hit.city()) && mine.id().equals(candidate.toNation())) {
                offer = candidate;
                break;
            }
        }
        if (offer == null) {
            return Messages.error(source, "command.colony_nations.secession.no_offer", hit.colonyId());
        }
        Nation from = data.registry().byId(offer.fromNation()).orElse(null);
        if (from == null || !from.cities().contains(offer.city())) {
            data.takeOffer(offer.city());
            return Messages.error(source, "command.colony_nations.city.unknown", hit.colonyId());
        }
        IColony colony = MineColoniesCities.findByCity(offer.city());
        if (colony == null) {
            data.takeOffer(offer.city());
            return Messages.error(source, "command.colony_nations.city.unknown", hit.colonyId());
        }
        data.takeOffer(offer.city());
        if (accept) {
            join(source.getServer(), data, from, mine, offer.city(), colony, player);
        } else {
            becomeFree(source.getServer(), data, from, offer.city(), colony);
        }
        return 1;
    }

    private static void join(
            MinecraftServer server,
            NationSavedData data,
            Nation from,
            Nation to,
            CityRef city,
            IColony colony,
            ServerPlayer newOwner) {
        long gameTime = server.overworld().getGameTime();
        Set<ChunkRef> claim = claimOf(colony, gameTime);
        Set<ChunkRef> moving = following(data, from, city, gameTime);
        moving.removeAll(claim);
        String label = label(colony, city);
        String fromName = from.name();
        data.registry().removeColony(from.id(), city);
        data.registry().addColony(to.id(), city);
        for (ChunkRef chunk : claim) {
            data.territory().release(chunk);
        }
        for (ChunkRef chunk : moving) {
            data.territory().release(chunk);
            data.territory().claim(chunk, to.id());
        }
        data.setDirty();
        colony.getPermissions().setOwner(newOwner);
        CitizenTaxHappiness.clear(colony);
        Nation joined = data.registry().byId(to.id()).orElse(to);
        CitizenTaxHappiness.apply(joined);
        TerritorySync.broadcastState(server, data, joined, moving, gameTime);
        TerritorySync.onCityLeft(server, data, colony, gameTime);
        tellEveryone(server, data, from.owner(), JOINED, label, fromName, joined.name());
    }

    private static void becomeFree(
            MinecraftServer server,
            NationSavedData data,
            Nation nation,
            CityRef city,
            IColony colony) {
        long gameTime = server.overworld().getGameTime();
        Set<ChunkRef> claim = claimOf(colony, gameTime);
        Set<ChunkRef> moving = following(data, nation, city, gameTime);
        moving.removeAll(claim);
        String label = label(colony, city);
        String fromName = nation.name();
        data.registry().removeColony(nation.id(), city);
        for (ChunkRef chunk : moving) {
            data.territory().release(chunk);
        }
        for (ChunkRef chunk : claim) {
            data.territory().release(chunk);
        }
        data.addFreeCity(new FreeCity(city, label, FreeCity.GRAY));
        if (colony != null) {
            CitizenTaxHappiness.clear(colony);
            colony.getPermissions().setOwnerAbandoned();
            ColonyNations.LOGGER.info(
                    "Colony {} is a free city. MineColonies has no owner-by-uuid call, so the owner is a new random id named [abandoned] and the previous player was removed from the colony ranks.",
                    city.colonyId());
        }
        data.setDirty();
        if (!moving.isEmpty()) {
            TerritorySync.releaseAll(server, moving);
        }
        if (colony != null) {
            TerritorySync.onCityLeft(server, data, colony, gameTime);
            TerritorySync.broadcastFreeCity(server, city, label, claim);
        }
        tellEveryone(server, data, nation.owner(), FREE, label, fromName);
    }

    private static Set<ChunkRef> following(NationSavedData data, Nation nation, CityRef city, long gameTime) {
        List<NearestCity.Anchor> anchors = new ArrayList<>();
        for (CityRef member : nation.cities()) {
            IColony colony = MineColoniesCities.findByCity(member);
            if (colony == null || colony.getCenter() == null) {
                continue;
            }
            BlockPos center = colony.getCenter();
            anchors.add(new NearestCity.Anchor(member, center.getX() >> 4, center.getZ() >> 4));
        }
        return new HashSet<>(SecessionLand.following(city, data.territory().chunks(nation.id()), anchors));
    }

    private static List<SecessionJoin.Candidate> neighbors(
            NationSavedData data,
            Nation leaving,
            Set<ChunkRef> claim,
            long gameTime) {
        List<SecessionJoin.Candidate> neighbors = new ArrayList<>();
        for (Nation other : data.registry().all()) {
            if (other.id().equals(leaving.id())) {
                continue;
            }
            Set<ChunkRef> land = new HashSet<>(data.territory().chunks(other.id()));
            for (CityRef city : other.cities()) {
                IColony colony = MineColoniesCities.findByCity(city);
                if (colony == null) {
                    continue;
                }
                land.addAll(ColonyClaimCache.get().chunks(colony, gameTime));
            }
            neighbors.add(new SecessionJoin.Candidate(other.id(), other.taxRate(), SecessionLand.nearest(claim, land)));
        }
        return neighbors;
    }

    private static void tellOffer(MinecraftServer server, NationSavedData data, Nation target, String cityName, int colonyId) {
        String id = Integer.toString(colonyId);
        ServerPlayer owner = server.getPlayerList().getPlayer(target.owner());
        if (owner == null) {
            data.addNotice(new PendingNotice(target.owner(), false, OFFER, List.of(cityName, target.name(), id, id)));
            return;
        }
        Messages.info(owner.createCommandSourceStack(), OFFER, cityName, target.name(), acceptButton(id), declineButton(id));
    }

    private static void tellEveryone(
            MinecraftServer server,
            NationSavedData data,
            UUID offlineOwner,
            String key,
            String... args) {
        boolean ownerOnline = offlineOwner == null;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (offlineOwner != null && offlineOwner.equals(player.getUUID())) {
                ownerOnline = true;
            }
            Messages.event(player, key, (Object[]) args);
        }
        if (!ownerOnline) {
            data.addNotice(new PendingNotice(offlineOwner, false, key, List.of(args)));
        }
    }

    private static Component acceptButton(String colonyId) {
        return Messages.run("command.colony_nations.secession.accept", "/nation accept-city " + colonyId);
    }

    private static Component declineButton(String colonyId) {
        return Messages.run("command.colony_nations.secession.decline", "/nation decline-city " + colonyId);
    }

    private static boolean pending(NationSavedData data, CityRef city) {
        for (SecessionOffer offer : data.offers()) {
            if (offer.city().equals(city)) {
                return true;
            }
        }
        return false;
    }

    private static Set<ChunkRef> claimOf(IColony colony, long gameTime) {
        if (colony == null) {
            return new HashSet<>();
        }
        return new HashSet<>(ColonyClaimCache.get().chunks(colony, gameTime));
    }

    private static String label(IColony colony, CityRef city) {
        if (colony == null || colony.getName() == null || colony.getName().isBlank()) {
            return "#" + city.colonyId();
        }
        return colony.getName();
    }

    private static long day(MinecraftServer server) {
        return server.overworld().getDayTime() / Upkeep.TICKS_PER_DAY;
    }
}
