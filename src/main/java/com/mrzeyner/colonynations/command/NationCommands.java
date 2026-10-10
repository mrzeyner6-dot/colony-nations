package com.mrzeyner.colonynations.command;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import com.mojang.brigadier.arguments.IntegerArgumentType;

import com.mrzeyner.colonynations.ColonyNations;
import com.mrzeyner.colonynations.Config;
import com.mrzeyner.colonynations.compat.minecolonies.NationCityCommands;
import com.mrzeyner.colonynations.compat.minecolonies.NationTerritoryCommands;
import com.mrzeyner.colonynations.compat.numismatics.NationTreasuryCommands;
import com.mrzeyner.colonynations.data.NationSavedData;
import com.mrzeyner.colonynations.nation.CityRef;
import com.mrzeyner.colonynations.nation.Nation;
import com.mrzeyner.colonynations.nation.NationException;
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
    private static final long TICKS_PER_SECOND = 20L;

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
                                .executes(NationCommands::create)))
                .then(Commands.literal("disband").executes(ctx -> disband(ctx, null)))
                .then(Commands.literal("rename")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(ctx -> rename(ctx, null, StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("color")
                        .then(Commands.argument("color", StringArgumentType.greedyString())
                                .executes(ctx -> color(ctx, null, StringArgumentType.getString(ctx, "color")))))
                .then(Commands.literal("info")
                        .executes(ctx -> info(ctx, null))
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(ctx -> info(ctx, StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("list").executes(NationCommands::list))
                .then(Commands.literal("city")
                        .then(Commands.literal("add")
                                .then(Commands.argument("colonyId", IntegerArgumentType.integer(1))
                                        .executes(NationCityCommands::add)))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("colonyId", IntegerArgumentType.integer(1))
                                        .executes(NationCityCommands::remove)))
                        .then(Commands.literal("list").executes(NationCityCommands::list)))
                .then(Commands.literal("capital")
                        .then(Commands.argument("colonyId", IntegerArgumentType.integer(1))
                                .executes(NationCityCommands::capital)))
                .then(NationTreasuryCommands.branch())
                .then(Commands.literal("annex").executes(NationTerritoryCommands::annex))
                .then(Commands.literal("unannex").executes(NationTerritoryCommands::unannex))
                .then(Commands.literal("territory").executes(NationTerritoryCommands::territory))
                .then(Commands.literal("admin")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("name", StringArgumentType.string())
                                .then(Commands.literal("disband")
                                        .executes(ctx -> disband(ctx, StringArgumentType.getString(ctx, "name"))))
                                .then(Commands.literal("rename")
                                        .then(Commands.argument("newName", StringArgumentType.string())
                                                .executes(ctx -> rename(
                                                        ctx,
                                                        StringArgumentType.getString(ctx, "name"),
                                                        StringArgumentType.getString(ctx, "newName")))))
                                .then(Commands.literal("color")
                                        .then(Commands.argument("color", StringArgumentType.greedyString())
                                                .executes(ctx -> color(
                                                        ctx,
                                                        StringArgumentType.getString(ctx, "name"),
                                                        StringArgumentType.getString(ctx, "color"))))))));
    }

    private static int create(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (saved(ctx).refuseChanges(ctx.getSource())) {
            return 0;
        }
        ServerPlayer player = player(ctx.getSource());
        if (player == null) {
            return Messages.error(ctx.getSource(), "command.colony_nations.players_only");
        }
        NationSavedData data = saved(ctx);
        NationRegistry registry = data.registry();
        try {
            Nation nation = registry.create(
                    StringArgumentType.getString(ctx, "name"),
                    player.getUUID(),
                    0xFFFFFF,
                    ctx.getSource().getLevel().getGameTime());
            data.setDirty();
            return Messages.event(ctx.getSource(), "command.colony_nations.create.success", nation.name());
        } catch (NationException exception) {
            return failRule(ctx.getSource(), exception);
        }
    }

    private static int disband(CommandContext<CommandSourceStack> ctx, String nationName) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        NationSavedData data = saved(ctx);
        if (data.refuseChanges(source)) {
            return 0;
        }
        Nation nation = resolveMutable(source, data.registry(), nationName);
        if (nation == null) {
            return 0;
        }
        long windowTicks = Config.DISBAND_CONFIRM_SECONDS.get() * TICKS_PER_SECOND;
        String key = actorKey(source) + ":" + nation.id();
        if (!DISBANDS.confirm(key, source.getLevel().getGameTime(), windowTicks)) {
            return Messages.info(
                    source,
                    "command.colony_nations.disband.confirm",
                    nation.name(),
                    Config.DISBAND_CONFIRM_SECONDS.get());
        }
        data.registry().delete(nation.id());
        data.territory().releaseAll(nation.id());
        data.setDirty();
        return Messages.success(source, "command.colony_nations.disband.success", nation.name());
    }

    private static int rename(CommandContext<CommandSourceStack> ctx, String nationName, String newName) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        NationSavedData data = saved(ctx);
        if (data.refuseChanges(source)) {
            return 0;
        }
        Nation nation = resolveMutable(source, data.registry(), nationName);
        if (nation == null) {
            return 0;
        }
        String previous = nation.name();
        try {
            Nation renamed = data.registry().rename(nation.id(), newName);
            data.setDirty();
            return Messages.success(source, "command.colony_nations.rename.success", previous, renamed.name());
        } catch (NationException exception) {
            return failRule(source, exception);
        }
    }

    private static int color(CommandContext<CommandSourceStack> ctx, String nationName, String rawColor) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        if (saved(ctx).refuseChanges(source)) {
            return 0;
        }
        Integer rgb = NationColors.parse(rawColor);
        if (rgb == null) {
            return Messages.error(source, "command.colony_nations.color.invalid");
        }
        NationSavedData data = saved(ctx);
        Nation nation = resolveMutable(source, data.registry(), nationName);
        if (nation == null) {
            return 0;
        }
        data.registry().setColor(nation.id(), rgb);
        data.setDirty();
        return Messages.success(source, "command.colony_nations.color.success", nation.name(), formatColor(rgb));
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
        Messages.line(source, "command.colony_nations.info.owner", ownerLabel(source, nation.owner()));
        Messages.line(source, "command.colony_nations.info.color", Messages.swatch(nation.color()), formatColor(nation.color()));
        Messages.line(source, "command.colony_nations.info.capital", capitalLabel(nation));
        Messages.line(source, "command.colony_nations.info.colonies", coloniesLabel(nation));
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
                    ownerLabel(source, nation.owner()),
                    nation.cities().size());
        }
        return 1;
    }

    /**
     * @param nationName null when the command targets the sender's own nation
     */
    private static Nation resolveMutable(CommandSourceStack source, NationRegistry registry, String nationName) throws CommandSyntaxException {
        if (nationName != null) {
            Nation nation = registry.byName(nationName).orElse(null);
            if (nation == null) {
                Messages.error(source, "command.colony_nations.unknown", nationName);
            }
            return nation;
        }
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
        if (!nation.owner().equals(player.getUUID())) {
            Messages.error(source, "command.colony_nations.not_owner");
            return null;
        }
        return nation;
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

    private static String actorKey(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = player(source);
        return player == null ? "console" : player.getUUID().toString();
    }

    private static NationSavedData saved(CommandContext<CommandSourceStack> ctx) {
        return NationSavedData.get(ctx.getSource().getServer());
    }

    private static int failRule(CommandSourceStack source, NationException exception) {
        String key = switch (exception.reason()) {
            case NAME_LENGTH -> "command.colony_nations.name_length";
            case NAME_TAKEN -> "command.colony_nations.name_taken";
            case OWNER_HAS_NATION -> "command.colony_nations.owner_has_nation";
            case UNKNOWN_NATION -> "command.colony_nations.unknown";
            case COLONY_TAKEN, UNKNOWN_COLONY -> "command.colony_nations.unknown";
        };
        return Messages.error(source, key);
    }

    private static String formatColor(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }

    private static String ownerLabel(CommandSourceStack source, UUID owner) {
        ServerPlayer online = source.getServer().getPlayerList().getPlayer(owner);
        return online == null ? owner.toString() : online.getGameProfile().getName();
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
