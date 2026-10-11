package com.mrzeyner.colonynations.compat.minecolonies;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import com.minecolonies.api.colony.IColony;

import com.mrzeyner.colonynations.command.Messages;
import com.mrzeyner.colonynations.data.NationSavedData;
import com.mrzeyner.colonynations.nation.CityRef;
import com.mrzeyner.colonynations.nation.Nation;
import com.mrzeyner.colonynations.nation.NationException;
import com.mrzeyner.colonynations.nation.NationRegistry;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/**
 * /nation city and /nation capital. The player must own both the nation and the colony.
 * The city argument is a colony number or name; omitted, it is the claim under the player.
 */
public final class NationCityCommands {
    private NationCityCommands() {
    }

    public static int add(CommandContext<CommandSourceStack> ctx, String raw) throws CommandSyntaxException {
        return changeMembership(ctx, raw, true);
    }

    public static int remove(CommandContext<CommandSourceStack> ctx, String raw) throws CommandSyntaxException {
        return changeMembership(ctx, raw, false);
    }

    public static int capital(CommandContext<CommandSourceStack> ctx, String raw) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayer)) {
            return Messages.error(source, "command.colony_nations.players_only");
        }
        CityArguments.Hit hit = CityArguments.resolve(source, raw, CityArguments.Pool.NATION);
        if (hit == null) {
            return 0;
        }
        NationSavedData data = NationSavedData.get(source.getServer());
        data.registry().setCapital(hit.nation().id(), hit.city());
        data.setDirty();
        return Messages.success(source, "command.colony_nations.capital.success", hit.nation().name(), hit.label(), hit.colonyId());
    }

    public static int list(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = player(source);
        if (player == null) {
            return Messages.error(source, "command.colony_nations.players_only");
        }
        Nation nation = NationSavedData.get(source.getServer()).registry().byOwner(player.getUUID()).orElse(null);
        if (nation == null) {
            return Messages.error(source, "command.colony_nations.info.none");
        }
        if (nation.cities().isEmpty()) {
            return Messages.error(source, "command.colony_nations.city.list.empty", nation.name());
        }
        List<CityRef> cities = new ArrayList<>(nation.cities());
        cities.sort(Comparator.comparing(CityRef::dimension).thenComparingInt(CityRef::colonyId));
        Messages.header(source, "command.colony_nations.city.list.header", nation.name());
        for (CityRef city : cities) {
            boolean capital = city.equals(nation.capital());
            String markKey = capital
                    ? "command.colony_nations.city.capital_mark"
                    : "command.colony_nations.city.no_mark";
            Messages.line(
                    source,
                    "command.colony_nations.city.list.entry",
                    colonyLabel(city),
                    Messages.suggest(
                            Integer.toString(city.colonyId()),
                            "/nation capital " + city.colonyId(),
                            "command.colony_nations.city.list.hover",
                            city.colonyId(),
                            city.dimension()),
                    city.dimension(),
                    Messages.plain(markKey),
                    CitizenTaxHappiness.format(MineColoniesCities.findByCity(city)),
                    nation.separatism(city),
                    Messages.plain(SeparatismDay.stageKey(nation.separatism(city))));
        }
        return 1;
    }

    private static int changeMembership(CommandContext<CommandSourceStack> ctx, String raw, boolean add) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return Messages.error(source, "command.colony_nations.players_only");
        }
        CityArguments.Pool pool = add ? CityArguments.Pool.OWNED : CityArguments.Pool.NATION;
        CityArguments.Hit hit = CityArguments.resolve(source, raw, pool);
        if (hit == null) {
            return 0;
        }
        IColony colony = hit.colony();
        if (colony == null) {
            return Messages.error(source, "command.colony_nations.city.unknown", hit.colonyId());
        }
        NationSavedData data = NationSavedData.get(source.getServer());
        NationRegistry registry = data.registry();
        Nation nation = registry.byOwner(player.getUUID()).orElse(null);
        if (nation == null) {
            return Messages.error(source, "command.colony_nations.info.none");
        }
        CityRef city = hit.city();
        try {
            if (add) {
                registry.addColony(nation.id(), city);
            } else if (!nation.cities().contains(city)) {
                return Messages.error(source, "command.colony_nations.capital.not_in_nation", hit.colonyId(), nation.name());
            } else {
                registry.removeColony(nation.id(), city);
            }
        } catch (NationException exception) {
            if (exception.reason() == NationException.Reason.COLONY_TAKEN) {
                return Messages.error(source, "command.colony_nations.city.taken", hit.colonyId());
            }
            return Messages.error(source, "command.colony_nations.city.unknown", hit.colonyId());
        }
        data.setDirty();
        if (add) {
            CityTerritory.onCityJoined(source.getServer(), data, colony, source.getLevel().getGameTime());
            CitizenTaxHappiness.apply(data.registry().byOwner(player.getUUID()).orElse(nation));
            return Messages.event(source, "command.colony_nations.city.added", colony.getName(), hit.colonyId(), nation.name());
        }
        CitizenTaxHappiness.clear(colony);
        Secession.dropOffer(data, city);
        TerritorySync.onCityLeft(source.getServer(), data, colony, source.getLevel().getGameTime());
        return Messages.success(source, "command.colony_nations.city.removed", colony.getName(), hit.colonyId(), nation.name());
    }

    private static String colonyLabel(CityRef city) {
        IColony colony = MineColoniesCities.findByCity(city);
        if (colony == null || colony.getName() == null || colony.getName().isBlank()) {
            return "#" + city.colonyId();
        }
        return colony.getName();
    }

    private static ServerPlayer player(CommandSourceStack source) throws CommandSyntaxException {
        if (source.getEntity() instanceof ServerPlayer player) {
            return player;
        }
        if (source.getEntity() == null) {
            return null;
        }
        return source.getPlayerOrException();
    }
}
