package com.mrzeyner.colonynations.network;

import com.mrzeyner.colonynations.ColonyNations;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * One button in the nation menu. The server checks the same rights as the matching command.
 * {@code text} is a name or a color. {@code amount} is a withdraw sum or a tax rate.
 * {@code dimension} and {@code colonyId} name a city or an offer.
 */
public record NationActionPayload(byte action, String text, long amount, String dimension, int colonyId)
        implements CustomPacketPayload {
    public static final byte CREATE = 1;
    public static final byte RENAME = 2;
    public static final byte COLOR = 3;
    public static final byte DISBAND = 4;
    public static final byte CAPITAL = 5;
    public static final byte REMOVE = 6;
    public static final byte ANNEX = 7;
    public static final byte UNANNEX = 8;
    public static final byte DEPOSIT_HAND = 9;
    public static final byte DEPOSIT_ALL = 10;
    public static final byte WITHDRAW = 11;
    public static final byte TAX = 12;
    public static final byte ACCEPT = 13;
    public static final byte DECLINE = 14;
    public static final byte BUY = 15;
    public static final byte TARIFF_DEFAULT = 16;
    public static final byte TARIFF = 17;
    public static final byte EMBARGO = 18;

    public static final Type<NationActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ColonyNations.MOD_ID, "nation_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NationActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BYTE,
            NationActionPayload::action,
            ByteBufCodecs.stringUtf8(MenuLimits.NAME),
            NationActionPayload::text,
            ByteBufCodecs.VAR_LONG,
            NationActionPayload::amount,
            ByteBufCodecs.stringUtf8(MenuLimits.DIMENSION),
            NationActionPayload::dimension,
            ByteBufCodecs.VAR_INT,
            NationActionPayload::colonyId,
            NationActionPayload::new);

    public NationActionPayload {
        text = text == null ? "" : text;
        dimension = dimension == null ? "" : dimension;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
