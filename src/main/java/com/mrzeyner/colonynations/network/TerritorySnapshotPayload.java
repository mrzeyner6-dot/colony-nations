package com.mrzeyner.colonynations.network;

import java.util.List;

import com.mrzeyner.colonynations.ColonyNations;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Full picture of nations plus every state and city chunk in the player's dimension.
 */
public record TerritorySnapshotPayload(List<NationStamp> nations, List<ChunkPlot> plots) implements CustomPacketPayload {
    public static final Type<TerritorySnapshotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ColonyNations.MOD_ID, "territory_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TerritorySnapshotPayload> STREAM_CODEC = StreamCodec.composite(
            NationStamp.STREAM_CODEC.apply(ByteBufCodecs.list()),
            TerritorySnapshotPayload::nations,
            ChunkPlot.STREAM_CODEC.apply(ByteBufCodecs.list()),
            TerritorySnapshotPayload::plots,
            TerritorySnapshotPayload::new);

    public TerritorySnapshotPayload {
        nations = List.copyOf(nations);
        plots = List.copyOf(plots);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
