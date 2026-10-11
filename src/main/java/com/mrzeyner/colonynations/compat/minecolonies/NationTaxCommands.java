package com.mrzeyner.colonynations.compat.minecolonies;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import com.minecolonies.api.colony.IColony;

import com.mrzeyner.colonynations.Config;
import com.mrzeyner.colonynations.command.Messages;
import com.mrzeyner.colonynations.data.NationSavedData;
import com.mrzeyner.colonynations.economy.Tax;
import com.mrzeyner.colonynations.nation.CityRef;
import com.mrzeyner.colonynations.nation.Nation;
import com.mrzeyner.colonynations.nation.NationRegistry;
import com.mrzeyner.colonynations.territory.Upkeep;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

/**
 * /nation tax and /nation tax set.
 */
public final class NationTaxCommands {
    private NationTaxCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> branch() {
        return Commands.literal("tax")
                .executes(NationTaxCommands::show)
                .then(Commands.literal("set")
                        .then(Commands.argument("rate", IntegerArgumentType.integer(0))
                                .executes(NationTaxCommands::set)));
    }

    private static int show(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Nation nation = ownNation(source, saved(ctx).registry());
        if (nation == null) {
            return 0;
        }
        int rate = Tax.bounded(nation.taxRate(), Config.MAX_TAX_RATE.get());
        List<CityRef> cities = new ArrayList<>(nation.cities());
        cities.sort(Comparator.comparing(CityRef::dimension).thenComparingInt(CityRef::colonyId));
        List<Integer> adults = new ArrayList<>();
        Messages.header(source, "command.colony_nations.tax.header", nation.name());
        Messages.line(source, "command.colony_nations.tax.rate", rate);
        for (CityRef city : cities) {
            IColony colony = MineColoniesCities.findByCity(city);
            int cityAdults = colony == null ? 0 : ColonyPopulation.adults(colony);
            adults.add(cityAdults);
            Messages.line(
                    source,
                    "command.colony_nations.tax.city",
                    colonyLabel(city, colony),
                    cityAdults,
                    Tax.income(cityAdults, rate));
        }
        Messages.line(source, "command.colony_nations.tax.total", Tax.bill(adults, rate).value());
        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        NationSavedData data = saved(ctx);
        if (data.refuseChanges(source)) {
            return 0;
        }
        Nation nation = ownNation(source, data.registry());
        if (nation == null) {
            return 0;
        }
        int maxRate = Config.MAX_TAX_RATE.get();
        int rate = IntegerArgumentType.getInteger(ctx, "rate");
        if (!Tax.accepted(rate, maxRate)) {
            return Messages.error(source, "command.colony_nations.tax.bounds", maxRate);
        }
        if (rate == nation.taxRate()) {
            return Messages.error(source, "command.colony_nations.tax.same", rate);
        }
        long today = source.getServer().overworld().getDayTime() / Upkeep.TICKS_PER_DAY;
        if (!Tax.canChange(nation.lastTaxChangeDay(), today)) {
            return Messages.error(source, "command.colony_nations.tax.cooldown");
        }
        Nation updated = data.registry().setTaxRate(nation.id(), rate, today);
        data.setDirty();
        return Messages.success(source, "command.colony_nations.tax.set", updated.name(), rate);
    }

    private static String colonyLabel(CityRef city, IColony colony) {
        if (colony == null || colony.getName() == null || colony.getName().isBlank()) {
            return "#" + city.colonyId();
        }
        return colony.getName();
    }

    private static Nation ownNation(CommandSourceStack source, NationRegistry registry) throws CommandSyntaxException {
        ServerPlayer player = player(source);
        if (player == null) {
            Messages.error(source, "command.colony_nations.players_only");
            return null;
        }
        Nation nation = registry.byOwner(player.getUUID()).orElse(null);
        if (nation == null) {
            Messages.error(source, "command.colony_nations.info.none");
            return null;
        }
        return nation;
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

    private static NationSavedData saved(CommandContext<CommandSourceStack> ctx) {
        return NationSavedData.get(ctx.getSource().getServer());
    }
}
