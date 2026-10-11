package com.mrzeyner.colonynations.network;

import com.mrzeyner.colonynations.ColonyNations;
import com.mrzeyner.colonynations.trade.TerminalStation;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Station and train lines for an open trade terminal. Names are clipped; an empty station means none is in range.
 */
public record TerminalVisitPayload(
        BlockPos pos,
        boolean station,
        String stationName,
        boolean train,
        String trainName,
        String nationName,
        int kind,
        boolean unowned) implements CustomPacketPayload {
    public static final Type<TerminalVisitPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ColonyNations.MOD_ID, "terminal_visit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalVisitPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBlockPos(payload.pos);
                buf.writeBoolean(payload.station);
                buf.writeUtf(payload.stationName, MenuLimits.LABEL);
                buf.writeBoolean(payload.train);
                buf.writeUtf(payload.trainName, MenuLimits.LABEL);
                buf.writeUtf(payload.nationName, MenuLimits.NAME);
                buf.writeVarInt(payload.kind);
                buf.writeBoolean(payload.unowned);
            },
            buf -> new TerminalVisitPayload(
                    buf.readBlockPos(),
                    buf.readBoolean(),
                    buf.readUtf(MenuLimits.LABEL),
                    buf.readBoolean(),
                    buf.readUtf(MenuLimits.LABEL),
                    buf.readUtf(MenuLimits.NAME),
                    buf.readVarInt(),
                    buf.readBoolean()));

    public TerminalVisitPayload {
        stationName = MenuLimits.clip(stationName, MenuLimits.LABEL);
        trainName = MenuLimits.clip(trainName, MenuLimits.LABEL);
        nationName = MenuLimits.clip(nationName, MenuLimits.NAME);
    }

    public TerminalStation.Kind visitKind() {
        TerminalStation.Kind[] values = TerminalStation.Kind.values();
        if (kind < 0 || kind >= values.length) {
            return TerminalStation.Kind.NONE;
        }
        return values[kind];
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
