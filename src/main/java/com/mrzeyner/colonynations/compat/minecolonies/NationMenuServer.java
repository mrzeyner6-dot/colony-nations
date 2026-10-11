package com.mrzeyner.colonynations.compat.minecolonies;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;

import com.mrzeyner.colonynations.Config;
import com.mrzeyner.colonynations.command.Messages;
import com.mrzeyner.colonynations.command.NationOperations;
import com.mrzeyner.colonynations.data.NationSavedData;
import com.mrzeyner.colonynations.data.FreeCity;
import com.mrzeyner.colonynations.data.SecessionOffer;
import com.mrzeyner.colonynations.economy.FreeCityBuy;
import com.mrzeyner.colonynations.economy.PopulationTax;
import com.mrzeyner.colonynations.economy.Separatism;
import com.mrzeyner.colonynations.nation.CityRef;
import com.mrzeyner.colonynations.nation.Nation;
import com.mrzeyner.colonynations.network.NationActionPayload;
import com.mrzeyner.colonynations.network.NationMenuRequestPayload;
import com.mrzeyner.colonynations.network.NationOverviewPayload;
import com.mrzeyner.colonynations.territory.Upkeep;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Serves the nation menu. Buttons run {@link NationOperations}, the same methods as the commands.
 */
public final class NationMenuServer {
    private NationMenuServer() {
    }

    public static void request(NationMenuRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                open(player, payload.open());
            }
        });
    }

    public static void action(NationActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            CommandSourceStack source = player.createCommandSourceStack();
            try {
                run(source, payload);
            } catch (CommandSyntaxException exception) {
                Messages.error(source, "command.colony_nations.players_only");
            }
            send(player, false);
        });
    }

    public static int open(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = NationOperations.player(source);
        if (player == null) {
            return Messages.error(source, "command.colony_nations.players_only");
        }
        return open(player, true);
    }

    private static int open(ServerPlayer player, boolean show) {
        NationSavedData data = NationSavedData.get(player.server);
        if (data.refuseChanges(player.createCommandSourceStack())) {
            return 0;
        }
        send(player, show);
        return 1;
    }

    private static void send(ServerPlayer player, boolean open) {
        NationSavedData data = NationSavedData.get(player.server);
        if (data.unreadable()) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new NationOverviewPayload(open, overview(player, data)));
    }

    private static int run(CommandSourceStack source, NationActionPayload payload) throws CommandSyntaxException {
        return switch (payload.action()) {
            case NationActionPayload.CREATE -> NationOperations.create(source, payload.text());
            case NationActionPayload.RENAME -> NationOperations.rename(source, null, payload.text());
            case NationActionPayload.COLOR -> NationOperations.color(source, null, payload.text());
            case NationActionPayload.DISBAND -> NationOperations.disbandNow(source);
            case NationActionPayload.CAPITAL -> NationOperations.makeCapital(source, payload.dimension(), payload.colonyId());
            case NationActionPayload.REMOVE -> NationOperations.removeCity(source, payload.dimension(), payload.colonyId());
            case NationActionPayload.ANNEX -> NationOperations.annex(source);
            case NationActionPayload.UNANNEX -> NationOperations.unannex(source);
            case NationActionPayload.DEPOSIT_HAND -> NationOperations.depositHand(source);
            case NationActionPayload.DEPOSIT_ALL -> NationOperations.depositAll(source);
            case NationActionPayload.WITHDRAW -> NationOperations.withdraw(source, payload.amount());
            case NationActionPayload.TAX -> NationOperations.setTax(source, (int) payload.amount());
            case NationActionPayload.ACCEPT -> NationOperations.acceptOffer(source, payload.dimension(), payload.colonyId());
            case NationActionPayload.DECLINE -> NationOperations.declineOffer(source, payload.dimension(), payload.colonyId());
            case NationActionPayload.BUY -> NationOperations.buyCity(source, payload.dimension(), payload.colonyId());
            default -> 0;
        };
    }

    private static NationOverviewPayload.Overview overview(ServerPlayer player, NationSavedData data) {
        Nation nation = data.registry().byOwner(player.getUUID()).orElse(null);
        if (nation == null) {
            return NationOverviewPayload.Overview.none();
        }
        long gameTime = player.serverLevel().getGameTime();
        NationTerritoryCommands.Preview land = NationTerritoryCommands.preview(player, nation, data, gameTime);
        long today = player.server.overworld().getDayTime() / Upkeep.TICKS_PER_DAY;
        boolean canTax = PopulationTax.canChange(nation.lastTaxChangeDay(), today);
        long ready = nation.lastTaxChangeDay() == null ? today : nation.lastTaxChangeDay() + 1L;
        List<NationOverviewPayload.CityLine> cities = cities(nation, today);
        int adults = 0;
        for (NationOverviewPayload.CityLine city : cities) {
            IColony colony = MineColoniesCities.findByCity(new CityRef(city.dimension(), city.colonyId()));
            adults += NationTaxes.adults(colony);
        }
        return new NationOverviewPayload.Overview(
                true,
                nation.name(),
                nation.color(),
                NationOperations.ownerLabel(player.createCommandSourceStack(), nation.owner()),
                capitalName(nation),
                nation.treasury(),
                nation.taxRate(),
                Config.MAX_TAX_RATE.get(),
                Config.TAX_NEUTRAL_RATE.get(),
                milli(Config.TAX_HAPPINESS_BONUS.get()),
                milli(Config.TAX_HAPPINESS_PENALTY.get()),
                adults,
                nation.cities().size(),
                land.cityChunks(),
                land.coreChunks(),
                land.annexed(),
                land.limit(),
                land.upkeep(),
                land.annexCode(),
                land.annexPrice(),
                land.unannexCode(),
                canTax,
                canTax ? today : ready,
                cities,
                offers(data, nation),
                frees(player, data, nation, gameTime));
    }

    private static List<NationOverviewPayload.CityLine> cities(Nation nation, long today) {
        List<CityRef> refs = new ArrayList<>(nation.cities());
        refs.sort(Comparator.comparing(CityRef::dimension).thenComparingInt(CityRef::colonyId));
        List<NationOverviewPayload.CityLine> lines = new ArrayList<>();
        int graceDays = Config.SEPARATISM_GRACE_DAYS.get();
        for (CityRef city : refs) {
            IColony colony = MineColoniesCities.findByCity(city);
            int left = Separatism.daysLeft(nation.graceDay(city), today, graceDays);
            double happiness = colony == null ? 10.0d : colony.getOverallHappiness();
            Separatism.Cause cause = Separatism.cause(
                    happiness,
                    Config.UNREST_THRESHOLD.get(),
                    Config.COLLAPSE_THRESHOLD.get(),
                    nation.taxRate(),
                    Config.TAX_NEUTRAL_RATE.get(),
                    left);
            lines.add(new NationOverviewPayload.CityLine(
                    city.dimension(),
                    city.colonyId(),
                    label(colony, city),
                    city.equals(nation.capital()),
                    citizens(colony),
                    happinessMilli(colony),
                    nation.separatism(city),
                    cause.ordinal(),
                    left));
        }
        return lines;
    }

    private static List<NationOverviewPayload.OfferLine> offers(NationSavedData data, Nation nation) {
        List<NationOverviewPayload.OfferLine> lines = new ArrayList<>();
        for (SecessionOffer offer : data.offers()) {
            if (!nation.id().equals(offer.toNation())) {
                continue;
            }
            lines.add(new NationOverviewPayload.OfferLine(
                    offer.city().dimension(),
                    offer.city().colonyId(),
                    offer.cityName(),
                    offer.offerDay() + 1L));
        }
        lines.sort(Comparator.comparing(NationOverviewPayload.OfferLine::dimension)
                .thenComparingInt(NationOverviewPayload.OfferLine::colonyId));
        return lines;
    }

    private static List<NationOverviewPayload.FreeLine> frees(
            ServerPlayer player,
            NationSavedData data,
            Nation nation,
            long gameTime) {
        long today = player.server.overworld().getDayTime() / com.mrzeyner.colonynations.territory.Upkeep.TICKS_PER_DAY;
        String here = player.level().dimension().location().toString();
        List<NationOverviewPayload.FreeLine> lines = new ArrayList<>();
        for (FreeCity free : data.freeCities()) {
            FreeCityBuy.Quote quote = FreeCityJoin.quote(data, nation, free, gameTime, today);
            boolean hereDimension = here.equals(free.city().dimension());
            if (quote.distance() == Integer.MAX_VALUE && !hereDimension) {
                continue;
            }
            IColony colony = MineColoniesCities.findByCity(free.city());
            String name = free.name().isBlank() ? "#" + free.city().colonyId() : free.name();
            lines.add(new NationOverviewPayload.FreeLine(
                    free.city().dimension(),
                    free.city().colonyId(),
                    name,
                    citizens(colony),
                    quote.distance(),
                    quote.price(),
                    quote.block(),
                    quote.readyDay()));
        }
        lines.sort(Comparator
                .comparingInt((NationOverviewPayload.FreeLine line) -> line.distance() == Integer.MAX_VALUE ? Integer.MAX_VALUE : line.distance())
                .thenComparing(NationOverviewPayload.FreeLine::name));
        return lines;
    }

    private static String capitalName(Nation nation) {
        if (nation.capital() == null) {
            return "—";
        }
        return label(MineColoniesCities.findByCity(nation.capital()), nation.capital());
    }

    private static String label(IColony colony, CityRef city) {
        if (colony == null || colony.getName() == null || colony.getName().isBlank()) {
            return "#" + city.colonyId();
        }
        return colony.getName();
    }

    private static int citizens(IColony colony) {
        if (colony == null || colony.getCitizenManager() == null) {
            return 0;
        }
        int count = 0;
        for (ICitizenData citizen : colony.getCitizenManager().getCitizens()) {
            if (citizen != null) {
                count++;
            }
        }
        return count;
    }

    private static int happinessMilli(IColony colony) {
        if (colony == null) {
            return -1;
        }
        return (int) Math.round(colony.getOverallHappiness() * 1000.0);
    }

    private static int milli(double value) {
        return (int) Math.round(value * 1000.0);
    }
}
