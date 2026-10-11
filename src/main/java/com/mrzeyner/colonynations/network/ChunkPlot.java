package com.mrzeyner.colonynations.network;

import java.util.UUID;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * One chunk in the player's current dimension.
 *
 * @param city true for MineColonies city land, false for state land
 * @param release true when the client should forget the chunk
 */
public record ChunkPlot(int x, int z, UUID nation, boolean city, boolean release) {
    public static final StreamCodec<RegistryFriendlyByteBuf, ChunkPlot> STREAM_CODEC = StreamCodec.of(
            (buf, plot) -> {
                buf.writeVarInt(plot.x());
                buf.writeVarInt(plot.z());
                buf.writeUUID(plot.nation());
                buf.writeBoolean(plot.city());
                buf.writeBoolean(plot.release());
            },
            buf -> new ChunkPlot(buf.readVarInt(), buf.readVarInt(), buf.readUUID(), buf.readBoolean(), buf.readBoolean()));

    public static ChunkPlot state(int x, int z, UUID nation) {
        return new ChunkPlot(x, z, nation, false, false);
    }

    public static ChunkPlot city(int x, int z, UUID nation) {
        return new ChunkPlot(x, z, nation, true, false);
    }

    public static ChunkPlot release(int x, int z) {
        return new ChunkPlot(x, z, new UUID(0L, 0L), false, true);
    }
}
