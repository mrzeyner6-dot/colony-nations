package com.mrzeyner.colonynations.network;

import java.util.UUID;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * A nation as the client needs it: id, name and color.
 */
public record NationStamp(UUID id, String name, int color) {
    public static final StreamCodec<RegistryFriendlyByteBuf, NationStamp> STREAM_CODEC = StreamCodec.of(
            (buf, stamp) -> {
                buf.writeUUID(stamp.id());
                buf.writeUtf(stamp.name());
                buf.writeInt(stamp.color());
            },
            buf -> new NationStamp(buf.readUUID(), buf.readUtf(), buf.readInt()));
}
