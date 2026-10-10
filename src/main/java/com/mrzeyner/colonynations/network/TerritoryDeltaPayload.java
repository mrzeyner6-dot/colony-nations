package com.mrzeyner.colonynations.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mrzeyner.colonynations.ColonyNations;
import com.mrzeyner.colonynations.client.TerritoryClientCache;
import com.mrzeyner.colonynations.territory.ChunkRef;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * One dimension's territory edits.
 * Each change starts with a byte: 0 release (x, z), 1 state or 2 city (x, z, uuid, name, color),
 * 3 nation meta or 4 forget nation (uuid, name, color; forget ignores the name).
 */
public record TerritoryDeltaPayload(String dimension, List<Change> changes) implements CustomPacketPayload {
    public static final byte RELEASE = 0;
    public static final byte STATE = 1;
    public static final byte CITY = 2;
    public static final byte NATION = 3;
    public static final byte FORGET = 4;

    public static final Type<TerritoryDeltaPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ColonyNations.MOD_ID, "territory_delta"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TerritoryDeltaPayload> STREAM_CODEC = StreamCodec.of(
            TerritoryDeltaPayload::encode,
            TerritoryDeltaPayload::decode);

    public record Change(byte op, int x, int z, UUID nation, String name, int color) {
        public static Change release(int x, int z) {
            return new Change(RELEASE, x, z, new UUID(0L, 0L), "", 0);
        }

        public static Change plot(byte op, int x, int z, UUID nation, String name, int color) {
            return new Change(op, x, z, nation, name, color);
        }

        public static Change nation(UUID nation, String name, int color) {
            return new Change(NATION, 0, 0, nation, name, color);
        }

        public static Change forget(UUID nation) {
            return new Change(FORGET, 0, 0, nation, "", 0);
        }
    }

    public static void encode(RegistryFriendlyByteBuf buf, TerritoryDeltaPayload payload) {
        buf.writeUtf(payload.dimension);
        buf.writeVarInt(payload.changes.size());
        for (Change change : payload.changes) {
            buf.writeByte(change.op);
            if (change.op == NATION || change.op == FORGET) {
                writeNation(buf, change);
                continue;
            }
            buf.writeInt(change.x);
            buf.writeInt(change.z);
            if (change.op != RELEASE) {
                writeNation(buf, change);
            }
        }
    }

    public static TerritoryDeltaPayload decode(RegistryFriendlyByteBuf buf) {
        String dimension = buf.readUtf();
        int count = buf.readVarInt();
        List<Change> changes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            byte op = buf.readByte();
            if (op == NATION || op == FORGET) {
                changes.add(readNation(op, 0, 0, buf));
                continue;
            }
            int x = buf.readInt();
            int z = buf.readInt();
            changes.add(op == RELEASE ? Change.release(x, z) : readNation(op, x, z, buf));
        }
        return new TerritoryDeltaPayload(dimension, changes);
    }

    public static void handle(TerritoryDeltaPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            for (Change change : payload.changes) {
                if (change.op == NATION) {
                    TerritoryClientCache.upsertNation(change.nation, change.name, change.color);
                    continue;
                }
                if (change.op == FORGET) {
                    TerritoryClientCache.removeNation(change.nation);
                    continue;
                }
                ChunkRef chunk = new ChunkRef(payload.dimension, change.x, change.z);
                if (change.op == RELEASE) {
                    TerritoryClientCache.removePlot(payload.dimension, chunk);
                } else {
                    TerritoryClientCache.upsertNation(change.nation, change.name, change.color);
                    TerritoryClientCache.putPlot(
                            payload.dimension,
                            chunk,
                            new TerritoryClientCache.Plot(change.nation, change.op == CITY));
                }
            }
        });
    }

    private static void writeNation(RegistryFriendlyByteBuf buf, Change change) {
        buf.writeLong(change.nation.getMostSignificantBits());
        buf.writeLong(change.nation.getLeastSignificantBits());
        buf.writeUtf(change.name);
        buf.writeInt(change.color);
    }

    private static Change readNation(byte op, int x, int z, RegistryFriendlyByteBuf buf) {
        return new Change(op, x, z, new UUID(buf.readLong(), buf.readLong()), buf.readUtf(), buf.readInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
