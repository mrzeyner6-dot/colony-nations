package com.mrzeyner.colonynations.compat.minecolonies;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.minecolonies.api.colony.IColony;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import com.mrzeyner.colonynations.Config;
import com.mrzeyner.colonynations.command.Messages;
import com.mrzeyner.colonynations.data.NationSavedData;
import com.mrzeyner.colonynations.economy.PopulationTax;
import com.mrzeyner.colonynations.nation.CityRef;
import com.mrzeyner.colonynations.nation.Nation;
import com.mrzeyner.colonynations.territory.Upkeep;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/**
 * /nation tax and /nation tax set. Only the nation owner can change the rate, and only once per game day.
 */
public final class NationTaxCommands {
    private NationTaxCommands() {
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
        List<CityLine> cities = lines(nation);
        long expected = PopulationTax.sum(adultsOf(cities), nation.taxRate()).income();
        Messages.header(source, "command.colony_nations.tax.header", nation.name());
        Messages.line(source, "command.colony_nations.tax.rate", nation.taxRate());
        if (cities.isEmpty()) {
            Messages.line(source, "command.colony_nations.tax.no_cities", nation.name());
        } else {
            for (CityLine city : cities) {
                long cityIncome = PopulationTax.sum(List.of(city.adults()), nation.taxRate()).income();
                Messages.line(source, "command.colony_nations.tax.city", city.name(), city.adults(), cityIncome);
            }
        }
        Messages.line(source, "command.colony_nations.tax.expected", expected);
        return 1;
    }

    public static int setRate(CommandSourceStack source, int rate) throws CommandSyntaxException {
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
        int max = Config.MAX_TAX_RATE.get();
        if (rate < 0 || rate > max) {
            return Messages.error(source, "command.colony_nations.tax.range", max);
        }
        long today = source.getServer().overworld().getDayTime() / Upkeep.TICKS_PER_DAY;
        if (!PopulationTax.canChange(nation.lastTaxChangeDay(), today)) {
            return Messages.error(source, "command.colony_nations.tax.cooldown");
        }
        Nation updated = data.registry().setTax(nation.id(), rate, today);
        data.setDirty();
        CitizenTaxHappiness.apply(updated);
        return Messages.success(source, "command.colony_nations.tax.set", nation.name(), rate);
    }

    /**
     * Rate applied when a nation is founded. Stays inside {@code 0…maxTaxRate}.
     */
    public static int foundingRate() {
        return PopulationTax.bounded(Config.DEFAULT_TAX_RATE.get(), Config.MAX_TAX_RATE.get());
    }

    private static List<CityLine> lines(Nation nation) {
        List<CityRef> cities = new ArrayList<>(nation.cities());
        cities.sort(Comparator.comparing(CityRef::dimension).thenComparingInt(CityRef::colonyId));
        List<CityLine> lines = new ArrayList<>();
        for (CityRef city : cities) {
            IColony colony = MineColoniesCities.findByCity(city);
            String name = colony == null || colony.getName() == null || colony.getName().isBlank()
                    ? "#" + city.colonyId()
                    : colony.getName();
            lines.add(new CityLine(name, NationTaxes.adults(colony)));
        }
        return lines;
    }

    private static List<Integer> adultsOf(List<CityLine> cities) {
        List<Integer> adults = new ArrayList<>();
        for (CityLine city : cities) {
            adults.add(city.adults());
        }
        return adults;
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

    private record CityLine(String name, int adults) {
    }
}
