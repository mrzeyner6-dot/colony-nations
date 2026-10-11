package com.mrzeyner.colonynations.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mrzeyner.colonynations.ColonyNations;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * A change since the last snapshot: nation identity, a removed nation, or chunks in one dimension.
 */
public record TerritoryDeltaPayload(List<NationStamp> nations, List<UUID> removedNations, List<ChunkPlot> plots)
        implements CustomPacketPayload {
    public static final Type<TerritoryDeltaPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ColonyNations.MOD_ID, "territory_delta"));

    private static final StreamCodec<RegistryFriendlyByteBuf, List<UUID>> UUIDS = StreamCodec.of(
            (buf, ids) -> {
                buf.writeVarInt(ids.size());
                for (UUID id : ids) {
                    buf.writeUUID(id);
                }
            },
            buf -> {
                int count = buf.readVarInt();
                List<UUID> ids = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    ids.add(buf.readUUID());
                }
                return ids;
            });

    public static final StreamCodec<RegistryFriendlyByteBuf, TerritoryDeltaPayload> STREAM_CODEC = StreamCodec.composite(
            NationStamp.STREAM_CODEC.apply(ByteBufCodecs.list()),
            TerritoryDeltaPayload::nations,
            UUIDS,
            TerritoryDeltaPayload::removedNations,
            ChunkPlot.STREAM_CODEC.apply(ByteBufCodecs.list()),
            TerritoryDeltaPayload::plots,
            TerritoryDeltaPayload::new);

    public TerritoryDeltaPayload {
        nations = List.copyOf(nations);
        removedNations = List.copyOf(removedNations);
        plots = List.copyOf(plots);
    }

    public boolean empty() {
        return nations.isEmpty() && removedNations.isEmpty() && plots.isEmpty();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
