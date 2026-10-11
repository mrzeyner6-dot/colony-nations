package com.mrzeyner.colonynations.network;

import com.mrzeyner.colonynations.ColonyNations;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Owner sets the spur price and the pack size. Strings stay short so a broken client cannot send a huge packet.
 */
public record TerminalSettingsPayload(BlockPos pos, String price, String pack) implements CustomPacketPayload {
    public static final Type<TerminalSettingsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ColonyNations.MOD_ID, "terminal_settings"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalSettingsPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBlockPos(payload.pos);
                buf.writeUtf(payload.price == null ? "" : payload.price, 16);
                buf.writeUtf(payload.pack == null ? "" : payload.pack, 16);
            },
            buf -> new TerminalSettingsPayload(buf.readBlockPos(), buf.readUtf(16), buf.readUtf(16)));

    public TerminalSettingsPayload {
        price = price == null ? "" : price;
        pack = pack == null ? "" : pack;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
