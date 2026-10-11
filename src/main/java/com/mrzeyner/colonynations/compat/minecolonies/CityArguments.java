package com.mrzeyner.colonynations.compat.minecolonies;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.claim.IChunkClaimData;
import com.minecolonies.api.util.constant.ColonyManagerConstants;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import com.mrzeyner.colonynations.command.Messages;
import com.mrzeyner.colonynations.data.NationSavedData;
import com.mrzeyner.colonynations.data.SecessionOffer;
import com.mrzeyner.colonynations.nation.CityRef;
import com.mrzeyner.colonynations.nation.Nation;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * City command argument: a colony number or a case-insensitive name, with suggestions.
 * An omitted argument means the claim the player is standing on.
 */
public final class CityArguments {
    public enum Pool {
        /** Colonies this player owns. Used by city add. */
        OWNED,
        /** Cities in this player's nation. Used by city remove and capital. */
        NATION,
        /** Cities offered to this player's nation. */
        OFFER,
        /** Every city that belongs to some nation. Used by operator commands. */
        MEMBER
    }

    public record Hit(Nation nation, CityRef city, IColony colony, String label) {
        public int colonyId() {
            return city.colonyId();
        }
    }

    private CityArguments() {
    }

    public static CompletableFuture<Suggestions> suggest(CommandSourceStack source, SuggestionsBuilder builder, Pool pool) {
        NationSavedData data = NationSavedData.get(source.getServer());
        if (data.unreadable()) {
            return builder.buildFuture();
        }
        ServerPlayer player = player(source);
        String remaining = builder.getRemainingLowerCase();
        Map<String, String> ids = new LinkedHashMap<>();
        for (Hit hit : options(data, player, pool)) {
            String id = Integer.toString(hit.colonyId());
            String tooltip = hit.label();
            String previous = ids.get(id);
            if (previous == null) {
                ids.put(id, tooltip);
            } else if (!previous.equals(tooltip)) {
                ids.put(id, previous + ", " + tooltip + " (" + hit.city().dimension() + ")");
            }
            if (!hit.label().isBlank() && !hit.label().equals(id) && hit.label().toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(quote(hit.label()), Component.literal("#" + id));
            }
        }
        for (Map.Entry<String, String> entry : ids.entrySet()) {
            if (entry.getKey().startsWith(builder.getRemaining())) {
                builder.suggest(entry.getKey(), Component.literal(entry.getValue()));
            }
        }
        return builder.buildFuture();
    }

    /**
     * @param raw colony number or name; null uses the claim under the player
     * @return null when the source has already been told why
     */
    public static Hit resolve(CommandSourceStack source, String raw, Pool pool) {
        NationSavedData data = NationSavedData.get(source.getServer());
        if (data.refuseChanges(source)) {
            return null;
        }
        ServerPlayer player = player(source);
        if (raw == null || raw.isBlank()) {
            if (player == null) {
                Messages.error(source, "command.colony_nations.players_only");
                return null;
            }
            Hit here = standing(player);
            if (here == null) {
                Messages.error(source, "command.colony_nations.city.here_none");
                return null;
            }
            Hit matched = pick(List.of(here), options(data, player, pool), player);
            if (matched == null) {
                explain(source, data, player, pool, Integer.toString(here.colonyId()), here.colonyId(), true);
                return null;
            }
            return matched;
        }
        String text = raw.trim();
        boolean numeric = !text.isEmpty() && text.chars().allMatch(Character::isDigit);
        int colonyId = -1;
        if (numeric) {
            try {
                colonyId = Integer.parseInt(text);
            } catch (NumberFormatException exception) {
                Messages.error(source, "command.colony_nations.city.unknown_name", text);
                return null;
            }
        }
        List<Hit> matches = new ArrayList<>();
        for (Hit hit : options(data, player, pool)) {
            if (numeric) {
                if (hit.colonyId() == colonyId) {
                    matches.add(hit);
                }
            } else if (hit.label().equalsIgnoreCase(text)) {
                matches.add(hit);
            }
        }
        Hit matched = pick(matches, matches, player);
        if (matched == null) {
            if (matches.size() > 1) {
                Messages.error(source, "command.colony_nations.city.ambiguous", text);
                return null;
            }
            explain(source, data, player, pool, text, colonyId, numeric);
            return null;
        }
        return matched;
    }

    private static Hit pick(List<Hit> matches, List<Hit> pool, ServerPlayer player) {
        if (matches.isEmpty()) {
            return null;
        }
        if (matches.size() == 1) {
            return inPool(matches.get(0), pool);
        }
        if (player == null) {
            return null;
        }
        String dimension = player.level().dimension().location().toString();
        Hit found = null;
        for (Hit hit : matches) {
            if (!dimension.equals(hit.city().dimension())) {
                continue;
            }
            if (found != null) {
                return null;
            }
            found = hit;
        }
        return found == null ? null : inPool(found, pool);
    }

    private static Hit inPool(Hit hit, List<Hit> pool) {
        for (Hit option : pool) {
            if (option.city().equals(hit.city())) {
                return option;
            }
        }
        return null;
    }

    private static void explain(
            CommandSourceStack source,
            NationSavedData data,
            ServerPlayer player,
            Pool pool,
            String text,
            int colonyId,
            boolean numeric) {
        Object shown = numeric ? colonyId : text;
        if (pool == Pool.OFFER) {
            Messages.error(source, "command.colony_nations.secession.no_offer", shown);
            return;
        }
        if (pool == Pool.NATION && player != null) {
            Nation nation = data.registry().byOwner(player.getUUID()).orElse(null);
            if (nation == null) {
                Messages.error(source, "command.colony_nations.info.none");
                return;
            }
            if (!colonyExists(shown, numeric)) {
                if (numeric) {
                    Messages.error(source, "command.colony_nations.city.unknown", colonyId);
                } else {
                    Messages.error(source, "command.colony_nations.city.unknown_name", text);
                }
                return;
            }
            Messages.error(source, "command.colony_nations.capital.not_in_nation", shown, nation.name());
            return;
        }
        if (pool == Pool.OWNED && colonyExists(shown, numeric)) {
            Messages.error(source, "command.colony_nations.city.not_owner", shown);
            return;
        }
        if (numeric) {
            Messages.error(source, "command.colony_nations.city.unknown", colonyId);
            return;
        }
        Messages.error(source, "command.colony_nations.city.unknown_name", text);
    }

    private static boolean colonyExists(Object shown, boolean numeric) {
        for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
            if (numeric && colony.getID() == (Integer) shown) {
                return true;
            }
            if (!numeric && colony.getName() != null && colony.getName().equalsIgnoreCase(String.valueOf(shown))) {
                return true;
            }
        }
        return false;
    }

    private static Hit standing(ServerPlayer player) {
        ResourceKey<Level> dimension = player.level().dimension();
        IChunkClaimData claim = IColonyManager.getInstance().getClaimData(dimension, new ChunkPos(player.blockPosition()));
        if (claim == null) {
            return null;
        }
        int colonyId = claim.getOwningColony();
        if (colonyId == ColonyManagerConstants.NO_COLONY_ID) {
            List<Integer> staticClaims = claim.getStaticClaimColonies();
            if (staticClaims == null || staticClaims.size() != 1) {
                return null;
            }
            colonyId = staticClaims.get(0);
        }
        String dimensionId = dimension.location().toString();
        for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
            if (colony.getID() == colonyId && dimension.equals(colony.getDimension())) {
                return hit(null, MineColoniesCities.cityRef(colony), colony);
            }
        }
        return hit(null, new CityRef(dimensionId, colonyId), null);
    }

    private static List<Hit> options(NationSavedData data, ServerPlayer player, Pool pool) {
        List<Hit> hits = new ArrayList<>();
        switch (pool) {
            case OWNED -> {
                if (player == null) {
                    break;
                }
                for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
                    if (player.getUUID().equals(colony.getPermissions().getOwner())) {
                        Nation nation = data.registry().byColony(MineColoniesCities.cityRef(colony)).orElse(null);
                        hits.add(hit(nation, MineColoniesCities.cityRef(colony), colony));
                    }
                }
            }
            case NATION -> {
                if (player == null) {
                    break;
                }
                Nation nation = data.registry().byOwner(player.getUUID()).orElse(null);
                if (nation != null) {
                    addNation(hits, nation);
                }
            }
            case OFFER -> {
                if (player == null) {
                    break;
                }
                Nation nation = data.registry().byOwner(player.getUUID()).orElse(null);
                if (nation == null) {
                    break;
                }
                for (SecessionOffer offer : data.offers()) {
                    if (!nation.id().equals(offer.toNation())) {
                        continue;
                    }
                    IColony colony = MineColoniesCities.findByCity(offer.city());
                    String label = offer.cityName().isBlank() ? label(colony, offer.city()) : offer.cityName();
                    hits.add(new Hit(nation, offer.city(), colony, label));
                }
            }
            case MEMBER -> {
                for (Nation nation : data.registry().all()) {
                    addNation(hits, nation);
                }
            }
        }
        return hits;
    }

    private static void addNation(List<Hit> hits, Nation nation) {
        for (CityRef city : nation.cities()) {
            IColony colony = MineColoniesCities.findByCity(city);
            hits.add(hit(nation, city, colony));
        }
    }

    private static Hit hit(Nation nation, CityRef city, IColony colony) {
        return new Hit(nation, city, colony, label(colony, city));
    }

    private static String label(IColony colony, CityRef city) {
        if (colony == null || colony.getName() == null || colony.getName().isBlank()) {
            return "#" + city.colonyId();
        }
        return colony.getName();
    }

    private static String quote(String name) {
        if (name.chars().anyMatch(Character::isWhitespace)) {
            return "\"" + name.replace("\"", "") + "\"";
        }
        return name;
    }

    private static ServerPlayer player(CommandSourceStack source) {
        return source.getEntity() instanceof ServerPlayer player ? player : null;
    }
}
