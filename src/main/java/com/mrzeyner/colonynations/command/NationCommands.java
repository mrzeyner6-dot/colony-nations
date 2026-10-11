package com.mrzeyner.colonynations.command;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import com.mojang.brigadier.arguments.IntegerArgumentType;

import com.mrzeyner.colonynations.ColonyNations;
import com.mrzeyner.colonynations.compat.minecolonies.CityArguments;
import com.mrzeyner.colonynations.compat.minecolonies.CitizenTaxHappiness;
import com.mrzeyner.colonynations.compat.minecolonies.NationCityCommands;
import com.mrzeyner.colonynations.compat.minecolonies.NationMenuServer;
import com.mrzeyner.colonynations.compat.minecolonies.NationDaily;
import com.mrzeyner.colonynations.compat.minecolonies.NationTaxCommands;
import com.mrzeyner.colonynations.compat.minecolonies.NationTerritoryCommands;
import com.mrzeyner.colonynations.compat.minecolonies.NationUpkeep;
import com.mrzeyner.colonynations.compat.minecolonies.Secession;
import com.mrzeyner.colonynations.compat.minecolonies.SeparatismDay;
import com.mrzeyner.colonynations.compat.numismatics.NationTreasuryCommands;
import com.mrzeyner.colonynations.data.NationSavedData;
import com.mrzeyner.colonynations.nation.CityRef;
import com.mrzeyner.colonynations.nation.Nation;
import com.mrzeyner.colonynations.nation.NationRegistry;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = ColonyNations.MOD_ID)
public final class NationCommands {
    private static final DisbandConfirmations DISBANDS = new DisbandConfirmations();

    private NationCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("nation")
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(ctx -> NationOperations.create(
                                        ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("disband").executes(ctx -> disband(ctx, null)))
                .then(Commands.literal("rename")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(ctx -> NationOperations.rename(
                                        ctx.getSource(), null, StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("color")
                        .then(Commands.argument("color", StringArgumentType.greedyString())
                                .executes(ctx -> NationOperations.color(
                                        ctx.getSource(), null, StringArgumentType.getString(ctx, "color")))))
                .then(Commands.literal("menu").executes(ctx -> NationMenuServer.open(ctx.getSource())))
                .then(Commands.literal("info")
                        .executes(ctx -> info(ctx, null))
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(ctx -> info(ctx, StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("list").executes(NationCommands::list))
                .then(Commands.literal("city")
                        .then(Commands.literal("add")
                                .executes(ctx -> NationCityCommands.add(ctx, null))
                                .then(Commands.argument("city", StringArgumentType.string())
                                        .suggests((ctx, builder) -> CityArguments.suggest(ctx.getSource(), builder, CityArguments.Pool.OWNED))
                                        .executes(ctx -> NationCityCommands.add(ctx, StringArgumentType.getString(ctx, "city")))))
                        .then(Commands.literal("remove")
                                .executes(ctx -> NationCityCommands.remove(ctx, null))
                                .then(Commands.argument("city", StringArgumentType.string())
                                        .suggests((ctx, builder) -> CityArguments.suggest(ctx.getSource(), builder, CityArguments.Pool.NATION))
                                        .executes(ctx -> NationCityCommands.remove(ctx, StringArgumentType.getString(ctx, "city")))))
                        .then(Commands.literal("list").executes(NationCityCommands::list))
                        .then(Commands.literal("buy")
                                .executes(ctx -> NationOperations.buyCity(ctx.getSource(), null))
                                .then(Commands.argument("city", StringArgumentType.string())
                                        .suggests((ctx, builder) -> CityArguments.suggest(
                                                ctx.getSource(), builder, CityArguments.Pool.FREE))
                                        .executes(ctx -> NationOperations.buyCity(
                                                ctx.getSource(), StringArgumentType.getString(ctx, "city"))))))
                .then(Commands.literal("capital")
                        .executes(ctx -> NationCityCommands.capital(ctx, null))
                        .then(Commands.argument("city", StringArgumentType.string())
                                .suggests((ctx, builder) -> CityArguments.suggest(ctx.getSource(), builder, CityArguments.Pool.NATION))
                                .executes(ctx -> NationCityCommands.capital(ctx, StringArgumentType.getString(ctx, "city")))))
                .then(NationTreasuryCommands.branch())
                .then(Commands.literal("annex").executes(ctx -> NationOperations.annex(ctx.getSource())))
                .then(Commands.literal("unannex").executes(ctx -> NationOperations.unannex(ctx.getSource())))
                .then(Commands.literal("territory").executes(NationTerritoryCommands::territory))
                .then(Commands.literal("accept-city")
                        .executes(ctx -> Secession.accept(ctx, null))
                        .then(Commands.argument("city", StringArgumentType.string())
                                .suggests((ctx, builder) -> CityArguments.suggest(ctx.getSource(), builder, CityArguments.Pool.OFFER))
                                .executes(ctx -> Secession.accept(ctx, StringArgumentType.getString(ctx, "city")))))
                .then(Commands.literal("decline-city")
                        .executes(ctx -> Secession.decline(ctx, null))
                        .then(Commands.argument("city", StringArgumentType.string())
                                .suggests((ctx, builder) -> CityArguments.suggest(ctx.getSource(), builder, CityArguments.Pool.OFFER))
                                .executes(ctx -> Secession.decline(ctx, StringArgumentType.getString(ctx, "city")))))
                .then(Commands.literal("tax")
                        .executes(NationTaxCommands::show)
                        .then(Commands.literal("set")
                                .then(Commands.argument("rate", IntegerArgumentType.integer(0))
                                        .executes(ctx -> NationOperations.setTax(
                                                ctx.getSource(), IntegerArgumentType.getInteger(ctx, "rate"))))))
                .then(Commands.literal("admin")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("name", StringArgumentType.string())
                                .then(Commands.literal("disband")
                                        .executes(ctx -> disband(ctx, StringArgumentType.getString(ctx, "name"))))
                                .then(Commands.literal("rename")
                                        .then(Commands.argument("newName", StringArgumentType.string())
                                                .executes(ctx -> NationOperations.rename(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "name"),
                                                        StringArgumentType.getString(ctx, "newName")))))
                                .then(Commands.literal("color")
                                        .then(Commands.argument("color", StringArgumentType.greedyString())
                                                .executes(ctx -> NationOperations.color(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "name"),
                                                        StringArgumentType.getString(ctx, "color"))))))
                        .then(Commands.literal("upkeep")
                                .then(Commands.literal("run").executes(NationUpkeep::runCommand)))
                        .then(Commands.literal("day")
                                .then(Commands.literal("run").executes(NationDaily::runCommand)))
                        .then(Commands.literal("city")
                                .then(Commands.literal("separatism")
                                        .then(Commands.argument("value", IntegerArgumentType.integer(0, 100))
                                                .executes(ctx -> SeparatismDay.set(ctx, null)))
                                        .then(Commands.argument("city", StringArgumentType.string())
                                                .suggests((ctx, builder) -> CityArguments.suggest(
                                                        ctx.getSource(), builder, CityArguments.Pool.MEMBER))
                                                .then(Commands.argument("value", IntegerArgumentType.integer(0, 100))
                                                        .executes(ctx -> SeparatismDay.set(
                                                                ctx, StringArgumentType.getString(ctx, "city"))))))
                                .then(Commands.literal("secede")
                                        .executes(ctx -> Secession.force(ctx, null))
                                        .then(Commands.argument("city", StringArgumentType.string())
                                                .suggests((ctx, builder) -> CityArguments.suggest(
                                                        ctx.getSource(), builder, CityArguments.Pool.MEMBER))
                                                .executes(ctx -> Secession.force(
                                                        ctx, StringArgumentType.getString(ctx, "city")))))
                                .then(Commands.literal("give")
                                        .then(Commands.argument("city", StringArgumentType.string())
                                                .suggests((ctx, builder) -> CityArguments.suggest(
                                                        ctx.getSource(), builder, CityArguments.Pool.FREE))
                                                .then(Commands.argument("nation", StringArgumentType.string())
                                                        .executes(ctx -> NationOperations.giveCity(
                                                                ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "city"),
                                                                StringArgumentType.getString(ctx, "nation")))))))));
    }

    private static int disband(CommandContext<CommandSourceStack> ctx, String nationName) throws CommandSyntaxException {
        return NationOperations.disband(ctx.getSource(), nationName, DISBANDS);
    }

    private static int info(CommandContext<CommandSourceStack> ctx, String nationName) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        NationRegistry registry = saved(ctx).registry();
        Nation nation;
        if (nationName == null) {
            ServerPlayer player = player(source);
            if (player == null) {
                return Messages.error(source, "command.colony_nations.players_only");
            }
            nation = registry.byOwner(player.getUUID()).orElse(null);
            if (nation == null) {
                return Messages.error(source, "command.colony_nations.info.none");
            }
        } else {
            nation = registry.byName(nationName).orElse(null);
            if (nation == null) {
                return Messages.error(source, "command.colony_nations.unknown", nationName);
            }
        }
        Messages.header(source, "command.colony_nations.info.header", nation.name());
        Messages.line(source, "command.colony_nations.info.owner", NationOperations.ownerLabel(source, nation.owner()));
        Messages.line(source, "command.colony_nations.info.color", Messages.swatch(nation.color()), formatColor(nation.color()));
        Messages.line(source, "command.colony_nations.info.capital", capitalLabel(nation));
        Messages.line(source, "command.colony_nations.info.colonies", coloniesLabel(nation));
        CitizenTaxHappiness.appendInfo(source, nation);
        Messages.line(source, "command.colony_nations.info.treasury", nation.treasury());
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        List<Nation> nations = new ArrayList<>(saved(ctx).registry().all());
        if (nations.isEmpty()) {
            return Messages.error(source, "command.colony_nations.list.empty");
        }
        nations.sort(Comparator.comparing(nation -> nation.name().toLowerCase(Locale.ROOT)));
        Messages.header(source, "command.colony_nations.list.header", nations.size());
        for (Nation nation : nations) {
            Messages.line(
                    source,
                    "command.colony_nations.list.entry",
                    nation.name(),
                    NationOperations.ownerLabel(source, nation.owner()),
                    nation.cities().size());
        }
        return 1;
    }

    private static ServerPlayer player(CommandSourceStack source) throws CommandSyntaxException {
        return NationOperations.player(source);
    }

    private static NationSavedData saved(CommandContext<CommandSourceStack> ctx) {
        return NationSavedData.get(ctx.getSource().getServer());
    }

    private static String formatColor(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }

    private static String capitalLabel(Nation nation) {
        return nation.capital() == null ? "—" : cityLabel(nation.capital());
    }

    private static String coloniesLabel(Nation nation) {
        if (nation.cities().isEmpty()) {
            return "—";
        }
        List<CityRef> cities = new ArrayList<>(nation.cities());
        cities.sort(Comparator.comparing(CityRef::dimension).thenComparingInt(CityRef::colonyId));
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < cities.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(cityLabel(cities.get(i)));
        }
        return builder.toString();
    }

    private static String cityLabel(CityRef city) {
        return city.dimension() + "#" + city.colonyId();
    }
}
