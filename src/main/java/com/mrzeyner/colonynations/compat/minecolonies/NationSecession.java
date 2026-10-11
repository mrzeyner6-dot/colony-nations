package com.mrzeyner.colonynations.compat.minecolonies;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.permissions.IPermissions;

import com.mrzeyner.colonynations.ColonyNations;
import com.mrzeyner.colonynations.Config;
import com.mrzeyner.colonynations.command.Messages;
import com.mrzeyner.colonynations.data.NationSavedData;
import com.mrzeyner.colonynations.data.PendingNotice;
import com.mrzeyner.colonynations.data.SecessionOffer;
import com.mrzeyner.colonynations.economy.Secession;
import com.mrzeyner.colonynations.economy.Tax;
import com.mrzeyner.colonynations.nation.CityRef;
import com.mrzeyner.colonynations.nation.FreeCity;
import com.mrzeyner.colonynations.nation.Nation;
import com.mrzeyner.colonynations.territory.ChunkRef;
import com.mrzeyner.colonynations.territory.NearestCity;
import com.mrzeyner.colonynations.territory.SecessionLand;
import com.mrzeyner.colonynations.territory.Upkeep;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Offers a seceding city to a cheaper neighbor, or makes it a free city.
 * A pending offer blocks a second one. Silence at the next game day is independence.
 * Transfer does not annex a founding ring; only the chunks {@link SecessionLand} picks move.
 */
public final class NationSecession {
    public static final String OFFER_KEY = "command.colony_nations.secession.offer";

    public enum Outcome {
        OFFERED,
        INDEPENDENT,
        ALREADY_PENDING
    }

    public record Begun(Outcome outcome, String cityName, String nationName) {
    }

    private NationSecession() {
    }

    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> acceptBranch() {
        return Commands.literal("accept-city")
                .then(Commands.argument("colonyId", IntegerArgumentType.integer(1))
                        .executes(NationSecession::accept));
    }

    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> declineBranch() {
        return Commands.literal("decline-city")
                .then(Commands.argument("colonyId", IntegerArgumentType.integer(1))
                        .executes(NationSecession::decline));
    }

    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> secedeBranch() {
        return Commands.literal("secede")
                .then(Commands.argument("colonyId", IntegerArgumentType.integer(1))
                        .executes(NationSecession::force));
    }

    /**
     * Closes offers whose day has arrived. Runs before taxes so a city that already left does not pay.
     */
    public static void expire(MinecraftServer server, long today) {
        NationSavedData data = NationSavedData.get(server);
        if (data.unreadable()) {
            return;
        }
        for (SecessionOffer offer : data.offers()) {
            if (Secession.offerOpen(today, offer.expiresOnDay())) {
                continue;
            }
            resolveSilence(server, data, offer);
        }
    }

    /**
     * Starts secession. Does nothing when this city already has an offer.
     */
    public static Begun begin(MinecraftServer server, NationSavedData data, Nation from, CityRef city, IColony colony) {
        String name = cityName(colony, city);
        if (data.offer(city).isPresent()) {
            return new Begun(Outcome.ALREADY_PENDING, name, "");
        }
        long gameTime = server.overworld().getGameTime();
        int maxRate = Config.MAX_TAX_RATE.get();
        Set<ChunkRef> claim = new HashSet<>(ColonyClaimCache.get().refresh(colony, gameTime));
        List<Secession.Neighbor> neighbors = new ArrayList<>();
        for (Nation other : data.registry().all()) {
            if (other.id().equals(from.id())) {
                continue;
            }
            neighbors.add(new Secession.Neighbor(
                    other.id(),
                    Tax.bounded(other.taxRate(), maxRate),
                    landOf(data, other, gameTime)));
        }
        Optional<Secession.Choice> choice = Secession.choose(
                Tax.bounded(from.taxRate(), maxRate),
                claim,
                neighbors,
                Config.SECESSION_JOIN_RADIUS.get());
        if (choice.isPresent()) {
            Nation target = data.registry().byId(choice.get().nation()).orElse(null);
            if (target != null) {
                offer(server, data, from, target, city, name);
                return new Begun(Outcome.OFFERED, name, target.name());
            }
        }
        independent(server, data, from, city, colony, claim);
        return new Begun(Outcome.INDEPENDENT, name, "");
    }

    /**
     * Drops a pending offer without moving the city. Used when the city leaves by some other command.
     */
    public static void forget(NationSavedData data, CityRef city) {
        data.offer(city).ifPresent(offer -> drop(data, offer));
    }

    /**
     * A nation is being removed. Offers it made are cancelled. Offers made to it become independence.
     */
    public static void onNationGone(MinecraftServer server, NationSavedData data, Nation nation) {
        for (SecessionOffer offer : data.offers()) {
            if (offer.fromNation().equals(nation.id())) {
                drop(data, offer);
            }
        }
        for (SecessionOffer offer : data.offers()) {
            if (offer.targetNation().equals(nation.id())) {
                resolveSilence(server, data, offer);
            }
        }
    }

    private static int accept(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return answer(ctx, true);
    }

    private static int decline(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return answer(ctx, false);
    }

    private static int answer(CommandContext<CommandSourceStack> ctx, boolean accept) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = player(source);
        if (player == null) {
            return Messages.error(source, "command.colony_nations.players_only");
        }
        NationSavedData data = NationSavedData.get(source.getServer());
        if (data.refuseChanges(source)) {
            return 0;
        }
        int colonyId = IntegerArgumentType.getInteger(ctx, "colonyId");
        Found found = findOffer(data, colonyId, player.level().dimension());
        if (found.ambiguous) {
            return Messages.error(source, "command.colony_nations.separatism.ambiguous", colonyId);
        }
        if (found.offer == null) {
            return Messages.error(source, "command.colony_nations.secession.none", colonyId);
        }
        if (!player.getUUID().equals(found.offer.targetOwner())) {
            return Messages.error(source, "command.colony_nations.not_owner");
        }
        long today = source.getServer().overworld().getDayTime() / Upkeep.TICKS_PER_DAY;
        if (!Secession.offerOpen(today, found.offer.expiresOnDay())) {
            resolveSilence(source.getServer(), data, found.offer);
            return Messages.error(source, "command.colony_nations.secession.expired", found.offer.cityName());
        }
        if (!accept) {
            resolveSilence(source.getServer(), data, found.offer);
            return 1;
        }
        if (!transfer(source.getServer(), data, found.offer, player)) {
            return Messages.error(source, "command.colony_nations.city.unknown", colonyId);
        }
        return 1;
    }

    private static int force(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        NationSavedData data = NationSavedData.get(source.getServer());
        if (data.refuseChanges(source)) {
            return 0;
        }
        int colonyId = IntegerArgumentType.getInteger(ctx, "colonyId");
        ResourceKey<Level> prefer = source.getEntity() instanceof ServerPlayer player
                ? player.level().dimension()
                : null;
        CityMatch match = findCity(data, colonyId, prefer);
        if (match.ambiguous) {
            return Messages.error(source, "command.colony_nations.separatism.ambiguous", colonyId);
        }
        if (match.nation == null || match.city == null) {
            return Messages.error(source, "command.colony_nations.city.unknown", colonyId);
        }
        IColony colony = MineColoniesCities.findByCity(match.city);
        if (colony == null) {
            return Messages.error(source, "command.colony_nations.city.unknown", colonyId);
        }
        forget(data, match.city);
        Begun begun = begin(source.getServer(), data, match.nation, match.city, colony);
        if (begun.outcome == Outcome.OFFERED) {
            return Messages.success(source, "command.colony_nations.secession.forced_offer", begun.cityName, begun.nationName);
        }
        if (!(source.getEntity() instanceof ServerPlayer)) {
            return Messages.success(source, "command.colony_nations.secession.forced_free", begun.cityName);
        }
        return 1;
    }

    private static void offer(
            MinecraftServer server,
            NationSavedData data,
            Nation from,
            Nation target,
            CityRef city,
            String name) {
        long today = server.overworld().getDayTime() / Upkeep.TICKS_PER_DAY;
        data.putOffer(new SecessionOffer(
                target.id(),
                target.owner(),
                city,
                from.id(),
                name,
                from.name(),
                target.name(),
                Secession.offerExpires(today)));
        String colonyId = Integer.toString(city.colonyId());
        List<String> args = List.of(name, target.name(), colonyId, colonyId);
        ServerPlayer owner = server.getPlayerList().getPlayer(target.owner());
        if (owner == null) {
            data.addNotice(new PendingNotice(target.owner(), false, OFFER_KEY, args));
            return;
        }
        Messages.event(owner, OFFER_KEY, args.toArray());
    }

    private static void resolveSilence(MinecraftServer server, NationSavedData data, SecessionOffer offer) {
        Nation from = data.registry().byId(offer.fromNation()).orElse(null);
        if (from == null || !from.cities().contains(offer.city())) {
            drop(data, offer);
            return;
        }
        IColony colony = MineColoniesCities.findByCity(offer.city());
        Set<ChunkRef> claim = colony == null
                ? Set.of()
                : ColonyClaimCache.get().chunks(colony, server.overworld().getGameTime());
        independent(server, data, from, offer.city(), colony, claim);
    }

    private static void independent(
            MinecraftServer server,
            NationSavedData data,
            Nation from,
            CityRef city,
            IColony colony,
            Set<ChunkRef> claim) {
        String name = cityName(colony, city);
        Set<ChunkRef> moving = SecessionLand.attributed(data.territory().chunks(from.id()), anchors(from), city);
        drop(data, city);
        if (colony != null) {
            abandon(colony);
            NationTaxHappiness.clear(colony);
        } else {
            ColonyNations.LOGGER.warn("Free city {} has no loaded colony; its MineColonies owner was left unchanged", city);
        }
        data.registry().removeColony(from.id(), city);
        Set<ChunkRef> released = releaseOwned(data, from.id(), claim, moving);
        data.putFreeCity(new FreeCity(city, colony == null || colony.getName() == null ? name : colony.getName(), FreeCity.GRAY));
        TerritorySync.releaseAll(server, released);
        TerritorySync.broadcastFreeCity(server, claim, city.colonyId(), name);
        Nation remaining = data.registry().byId(from.id()).orElse(from);
        TerritorySync.relabelState(server, data, remaining, city.dimension(), server.overworld().getGameTime());
        data.setDirty();
        tellEveryone(server, "command.colony_nations.secession.free", name);
    }

    private static boolean transfer(MinecraftServer server, NationSavedData data, SecessionOffer offer, ServerPlayer player) {
        Nation from = data.registry().byId(offer.fromNation()).orElse(null);
        Nation target = data.registry().byOwner(player.getUUID()).orElse(null);
        if (from == null || target == null || !target.id().equals(offer.targetNation()) || !from.cities().contains(offer.city())) {
            drop(data, offer);
            return false;
        }
        IColony colony = MineColoniesCities.findByCity(offer.city());
        if (colony == null) {
            drop(data, offer);
            return false;
        }
        long gameTime = server.overworld().getGameTime();
        Set<ChunkRef> claim = new HashSet<>(ColonyClaimCache.get().chunks(colony, gameTime));
        Set<ChunkRef> moving = SecessionLand.attributed(data.territory().chunks(from.id()), anchors(from), offer.city());
        giveColony(colony, player);
        data.registry().removeColony(from.id(), offer.city());
        data.registry().addColony(target.id(), offer.city());
        for (ChunkRef chunk : moving) {
            data.territory().release(chunk);
            data.territory().claim(chunk, target.id());
        }
        Set<ChunkRef> covered = releaseOwned(data, from.id(), claim, Set.of());
        drop(data, offer);
        NationTaxHappiness.clear(colony);
        Nation joined = data.registry().byId(target.id()).orElseThrow();
        NationTaxHappiness.refresh(server, joined);
        TerritorySync.releaseAll(server, covered);
        TerritorySync.broadcastState(server, data, joined, moving, gameTime);
        String name = cityName(colony, offer.city());
        TerritorySync.broadcastCity(server, joined, claim, offer.city().colonyId(), name);
        Nation left = data.registry().byId(from.id()).orElse(from);
        TerritorySync.relabelState(server, data, left, offer.city().dimension(), gameTime);
        data.setDirty();
        tellEveryone(server, "command.colony_nations.secession.joined", name, joined.name());
        return true;
    }

    /**
     * {@code setOwner} removes the previous owner from the player map, so they are not left as an officer.
     */
    private static void giveColony(IColony colony, ServerPlayer player) {
        IPermissions permissions = colony.getPermissions();
        UUID previous = permissions.getOwner();
        permissions.setOwner(player);
        if (previous != null && !previous.equals(player.getUUID()) && permissions.getPlayers().containsKey(previous)) {
            permissions.removePlayer(previous);
        }
    }

    /**
     * MineColonies has no way to set a chosen non-player owner. {@code setOwnerAbandoned} removes the
     * current owner and stores a random UUID under the name "[abandoned]". Free cities are matched by {@link CityRef}.
     */
    private static void abandon(IColony colony) {
        IPermissions permissions = colony.getPermissions();
        UUID previous = permissions.getOwner();
        permissions.setOwnerAbandoned();
        if (previous != null && permissions.getPlayers().containsKey(previous)) {
            permissions.removePlayer(previous);
        }
        ColonyNations.LOGGER.info(
                "Free city colony {} is abandoned in MineColonies (owner {}, name {}). Colony Nations matches it by city ref, not by that random id.",
                colony.getID(),
                permissions.getOwner(),
                permissions.getOwnerName());
    }

    private static void drop(NationSavedData data, CityRef city) {
        data.offer(city).ifPresent(offer -> drop(data, offer));
    }

    private static void drop(NationSavedData data, SecessionOffer offer) {
        data.removeNotices(offer.targetOwner(), OFFER_KEY, Integer.toString(offer.city().colonyId()));
        data.removeOffer(offer.city());
    }

    /**
     * Drops this nation's claim on {@code moving} and on city chunks the colony still covers.
     * City chunks are not given to the new nation unless they were already in {@code moving}.
     */
    private static Set<ChunkRef> releaseOwned(NationSavedData data, UUID nationId, Set<ChunkRef> claim, Set<ChunkRef> moving) {
        Set<ChunkRef> released = new HashSet<>(moving);
        for (ChunkRef chunk : moving) {
            data.territory().release(chunk);
        }
        for (ChunkRef chunk : claim) {
            if (nationId.equals(data.territory().owner(chunk).orElse(null))) {
                data.territory().release(chunk);
                released.add(chunk);
            }
        }
        return released;
    }

    private static Set<ChunkRef> landOf(NationSavedData data, Nation nation, long gameTime) {
        Set<ChunkRef> land = new HashSet<>(data.territory().chunks(nation.id()));
        for (CityRef city : nation.cities()) {
            IColony colony = MineColoniesCities.findByCity(city);
            if (colony != null) {
                land.addAll(ColonyClaimCache.get().refresh(colony, gameTime));
            }
        }
        return land;
    }

    private static List<NearestCity.Anchor> anchors(Nation nation) {
        List<NearestCity.Anchor> anchors = new ArrayList<>();
        for (CityRef city : nation.cities()) {
            IColony colony = MineColoniesCities.findByCity(city);
            if (colony == null) {
                continue;
            }
            BlockPos center = colony.getCenter();
            anchors.add(new NearestCity.Anchor(city, center.getX() >> 4, center.getZ() >> 4));
        }
        return anchors;
    }

    private static void tellEveryone(MinecraftServer server, String key, Object... args) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Messages.event(player, key, args);
        }
    }

    private static String cityName(IColony colony, CityRef city) {
        if (colony == null || colony.getName() == null || colony.getName().isBlank()) {
            return "#" + city.colonyId();
        }
        return colony.getName();
    }

    private static Found findOffer(NationSavedData data, int colonyId, ResourceKey<Level> prefer) {
        List<SecessionOffer> matches = new ArrayList<>();
        for (SecessionOffer offer : data.offers()) {
            if (offer.city().colonyId() == colonyId) {
                matches.add(offer);
            }
        }
        if (matches.isEmpty()) {
            return new Found(null, false);
        }
        if (matches.size() == 1) {
            return new Found(matches.get(0), false);
        }
        if (prefer != null) {
            String dimension = prefer.location().toString();
            SecessionOffer found = null;
            for (SecessionOffer offer : matches) {
                if (!dimension.equals(offer.city().dimension())) {
                    continue;
                }
                if (found != null) {
                    return new Found(null, true);
                }
                found = offer;
            }
            if (found != null) {
                return new Found(found, false);
            }
        }
        return new Found(null, true);
    }

    private static CityMatch findCity(NationSavedData data, int colonyId, ResourceKey<Level> prefer) {
        List<CityMatch> matches = new ArrayList<>();
        for (Nation nation : data.registry().all()) {
            for (CityRef city : nation.cities()) {
                if (city.colonyId() == colonyId) {
                    matches.add(new CityMatch(nation, city, false));
                }
            }
        }
        if (matches.isEmpty()) {
            return new CityMatch(null, null, false);
        }
        if (matches.size() == 1) {
            return matches.get(0);
        }
        if (prefer != null) {
            String dimension = prefer.location().toString();
            CityMatch found = null;
            for (CityMatch match : matches) {
                if (!dimension.equals(match.city.dimension())) {
                    continue;
                }
                if (found != null) {
                    return new CityMatch(null, null, true);
                }
                found = match;
            }
            if (found != null) {
                return found;
            }
        }
        return new CityMatch(null, null, true);
    }

    private static ServerPlayer player(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer playing) {
            return playing;
        }
        return null;
    }

    private record Found(SecessionOffer offer, boolean ambiguous) {
    }

    private record CityMatch(Nation nation, CityRef city, boolean ambiguous) {
    }
}
