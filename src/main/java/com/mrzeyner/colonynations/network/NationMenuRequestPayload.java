package com.mrzeyner.colonynations.network;

import com.mrzeyner.colonynations.ColonyNations;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client asks for a fresh {@link NationOverviewPayload}. {@code open} is true when the window should appear.
 */
public record NationMenuRequestPayload(boolean open) implements CustomPacketPayload {
    public static final Type<NationMenuRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ColonyNations.MOD_ID, "nation_menu_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NationMenuRequestPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeBoolean(payload.open),
            buf -> new NationMenuRequestPayload(buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
