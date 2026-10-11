package com.mrzeyner.colonynations.network;

import java.util.ArrayList;
import java.util.List;

import com.mrzeyner.colonynations.ColonyNations;

import io.netty.handler.codec.DecoderException;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Everything the nation menu draws. {@code open} asks the client to show the window;
 * a refresh after a button leaves it false so a closed window stays closed.
 */
public record NationOverviewPayload(boolean open, Overview overview) implements CustomPacketPayload {
    public static final Type<NationOverviewPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ColonyNations.MOD_ID, "nation_overview"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NationOverviewPayload> STREAM_CODEC = StreamCodec.of(
            NationOverviewPayload::encode,
            NationOverviewPayload::decode);

    public record CityLine(
            String dimension,
            int colonyId,
            String name,
            boolean capital,
            int citizens,
            int happinessMilli,
            int separatism) {
        public CityLine {
            dimension = MenuLimits.clip(dimension, MenuLimits.DIMENSION);
            name = MenuLimits.clip(name, MenuLimits.LABEL);
        }
    }

    public record OfferLine(String dimension, int colonyId, String name, long deadlineDay) {
        public OfferLine {
            dimension = MenuLimits.clip(dimension, MenuLimits.DIMENSION);
            name = MenuLimits.clip(name, MenuLimits.LABEL);
        }
    }

    /**
     * @param block {@link com.mrzeyner.colonynations.economy.FreeCityBuy} buy code
     * @param distance Chebyshev chunks, or {@link Integer#MAX_VALUE} when the nation has no land in that dimension
     */
    public record FreeLine(
            String dimension,
            int colonyId,
            String name,
            int citizens,
            int distance,
            long price,
            int block,
            long readyDay) {
        public FreeLine {
            dimension = MenuLimits.clip(dimension, MenuLimits.DIMENSION);
            name = MenuLimits.clip(name, MenuLimits.LABEL);
        }
    }

    /**
     * @param happinessMilli citizen happiness times 1000, or {@code -1} when the colony is missing
     * @param bonusMilli tax-happiness bonus at rate 0, times 1000
     * @param penaltyMilli tax-happiness factor at the maximum rate, times 1000
     * @param annexCode {@link com.mrzeyner.colonynations.compat.minecolonies.NationTerritoryCommands.Preview} annex code
     * @param unannexCode the matching release code
     */
    public record Overview(
            boolean member,
            String name,
            int color,
            String owner,
            String capital,
            long treasury,
            int taxRate,
            int maxTax,
            int neutralTax,
            int bonusMilli,
            int penaltyMilli,
            int adults,
            int cityCount,
            int cityChunks,
            int coreChunks,
            int annexed,
            int limit,
            long upkeep,
            int annexCode,
            long annexPrice,
            int unannexCode,
            boolean canTax,
            long taxReadyDay,
            List<CityLine> cities,
            List<OfferLine> offers,
            List<FreeLine> frees) {
        public Overview {
            name = MenuLimits.clip(name, MenuLimits.LABEL);
            owner = MenuLimits.clip(owner, MenuLimits.LABEL);
            capital = MenuLimits.clip(capital, MenuLimits.LABEL);
            cities = cities == null ? List.of() : List.copyOf(bound(cities, MenuLimits.CITIES));
            offers = offers == null ? List.of() : List.copyOf(bound(offers, MenuLimits.OFFERS));
            frees = frees == null ? List.of() : List.copyOf(bound(frees, MenuLimits.FREE));
        }

        public static Overview none() {
            return new Overview(
                    false, "", 0xFFFFFF, "", "", 0L, 0, 0, 0, 1000, 1000, 0,
                    0, 0, 0, 0, 0, 0L, 0, 0L, 0, false, 0L, List.of(), List.of(), List.of());
        }
    }

    private static void encode(RegistryFriendlyByteBuf buf, NationOverviewPayload payload) {
        Overview view = payload.overview == null ? Overview.none() : payload.overview;
        buf.writeBoolean(payload.open);
        buf.writeBoolean(view.member);
        buf.writeUtf(view.name);
        buf.writeInt(view.color);
        buf.writeUtf(view.owner);
        buf.writeUtf(view.capital);
        buf.writeVarLong(view.treasury);
        buf.writeVarInt(view.taxRate);
        buf.writeVarInt(view.maxTax);
        buf.writeVarInt(view.neutralTax);
        buf.writeVarInt(view.bonusMilli);
        buf.writeVarInt(view.penaltyMilli);
        buf.writeVarInt(view.adults);
        buf.writeVarInt(view.cityCount);
        buf.writeVarInt(view.cityChunks);
        buf.writeVarInt(view.coreChunks);
        buf.writeVarInt(view.annexed);
        buf.writeVarInt(view.limit);
        buf.writeVarLong(view.upkeep);
        buf.writeByte(view.annexCode);
        buf.writeVarLong(view.annexPrice);
        buf.writeByte(view.unannexCode);
        buf.writeBoolean(view.canTax);
        buf.writeVarLong(view.taxReadyDay);
        buf.writeVarInt(view.cities.size());
        for (CityLine city : view.cities) {
            buf.writeUtf(city.dimension);
            buf.writeVarInt(city.colonyId);
            buf.writeUtf(city.name);
            buf.writeBoolean(city.capital);
            buf.writeVarInt(city.citizens);
            buf.writeVarInt(city.happinessMilli);
            buf.writeVarInt(city.separatism);
        }
        buf.writeVarInt(view.offers.size());
        for (OfferLine offer : view.offers) {
            buf.writeUtf(offer.dimension);
            buf.writeVarInt(offer.colonyId);
            buf.writeUtf(offer.name);
            buf.writeVarLong(offer.deadlineDay);
        }
        buf.writeVarInt(view.frees.size());
        for (FreeLine free : view.frees) {
            buf.writeUtf(free.dimension);
            buf.writeVarInt(free.colonyId);
            buf.writeUtf(free.name);
            buf.writeVarInt(free.citizens);
            buf.writeVarInt(free.distance);
            buf.writeVarLong(free.price);
            buf.writeByte(free.block);
            buf.writeVarLong(free.readyDay);
        }
    }

    private static NationOverviewPayload decode(RegistryFriendlyByteBuf buf) {
        boolean open = buf.readBoolean();
        boolean member = buf.readBoolean();
        Overview view = new Overview(
                member,
                buf.readUtf(MenuLimits.LABEL),
                buf.readInt(),
                buf.readUtf(MenuLimits.LABEL),
                buf.readUtf(MenuLimits.LABEL),
                buf.readVarLong(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarLong(),
                buf.readByte(),
                buf.readVarLong(),
                buf.readByte(),
                buf.readBoolean(),
                buf.readVarLong(),
                readCities(buf),
                readOffers(buf),
                readFrees(buf));
        return new NationOverviewPayload(open, view);
    }

    private static List<CityLine> readCities(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MenuLimits.CITIES) {
            throw new DecoderException("too many cities");
        }
        List<CityLine> cities = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            cities.add(new CityLine(
                    buf.readUtf(MenuLimits.DIMENSION),
                    buf.readVarInt(),
                    buf.readUtf(MenuLimits.LABEL),
                    buf.readBoolean(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt()));
        }
        return cities;
    }

    private static List<OfferLine> readOffers(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MenuLimits.OFFERS) {
            throw new DecoderException("too many offers");
        }
        List<OfferLine> offers = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            offers.add(new OfferLine(
                    buf.readUtf(MenuLimits.DIMENSION),
                    buf.readVarInt(),
                    buf.readUtf(MenuLimits.LABEL),
                    buf.readVarLong()));
        }
        return offers;
    }

    private static List<FreeLine> readFrees(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MenuLimits.FREE) {
            throw new DecoderException("too many free cities");
        }
        List<FreeLine> frees = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            frees.add(new FreeLine(
                    buf.readUtf(MenuLimits.DIMENSION),
                    buf.readVarInt(),
                    buf.readUtf(MenuLimits.LABEL),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarLong(),
                    buf.readByte(),
                    buf.readVarLong()));
        }
        return frees;
    }

    private static <T> List<T> bound(List<T> values, int max) {
        if (values.size() <= max) {
            return values;
        }
        return values.subList(0, max);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
