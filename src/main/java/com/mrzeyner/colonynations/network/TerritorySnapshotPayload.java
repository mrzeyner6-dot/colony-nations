package com.mrzeyner.colonynations.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * Full territory of one dimension.
 * Layout: utf dimension, varint nations (uuid, utf name, int color),
 * varint plots (int x, int z, varint nation index, byte kind).
 * Kind 0 is annexed state land. Kind 1 is city land and kind 2 is the city core;
 * those two also carry a varint colony id and the colony name.
 */
public record TerritorySnapshotPayload(String dimension, List<NationPart> nations, List<PlotPart> plots)
        implements CustomPacketPayload {
    public static final Type<TerritorySnapshotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ColonyNations.MOD_ID, "territory_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TerritorySnapshotPayload> STREAM_CODEC = StreamCodec.of(
            TerritorySnapshotPayload::encode,
            TerritorySnapshotPayload::decode);

    public record NationPart(UUID id, String name, int color) {
    }

    public static final byte STATE = 0;
    public static final byte CITY = 1;
    public static final byte CORE = 2;

    public record PlotPart(int x, int z, int nationIndex, byte kind, int colonyId, String placeName, boolean unrest) {
    }

    public static void encode(RegistryFriendlyByteBuf buf, TerritorySnapshotPayload payload) {
        buf.writeUtf(payload.dimension);
        buf.writeVarInt(payload.nations.size());
        for (NationPart nation : payload.nations) {
            buf.writeLong(nation.id.getMostSignificantBits());
            buf.writeLong(nation.id.getLeastSignificantBits());
            buf.writeUtf(nation.name);
            buf.writeInt(nation.color);
        }
        buf.writeVarInt(payload.plots.size());
        for (PlotPart plot : payload.plots) {
            buf.writeInt(plot.x);
            buf.writeInt(plot.z);
            buf.writeVarInt(plot.nationIndex);
            buf.writeByte(plot.kind);
            if (plot.kind != STATE) {
                buf.writeVarInt(plot.colonyId);
                buf.writeUtf(plot.placeName == null ? "" : plot.placeName);
                if (plot.kind == CITY) {
                    buf.writeBoolean(plot.unrest);
                }
            }
        }
    }

    public static TerritorySnapshotPayload decode(RegistryFriendlyByteBuf buf) {
        String dimension = buf.readUtf();
        int nationCount = buf.readVarInt();
        List<NationPart> nations = new ArrayList<>(nationCount);
        for (int i = 0; i < nationCount; i++) {
            nations.add(new NationPart(new UUID(buf.readLong(), buf.readLong()), buf.readUtf(), buf.readInt()));
        }
        int plotCount = buf.readVarInt();
        List<PlotPart> plots = new ArrayList<>(plotCount);
        for (int i = 0; i < plotCount; i++) {
            int x = buf.readInt();
            int z = buf.readInt();
            int nationIndex = buf.readVarInt();
            byte kind = buf.readByte();
            int colonyId = -1;
            String placeName = "";
            boolean unrest = false;
            if (kind != STATE) {
                colonyId = buf.readVarInt();
                placeName = buf.readUtf();
                if (kind == CITY) {
                    unrest = buf.readBoolean();
                }
            }
            plots.add(new PlotPart(x, z, nationIndex, kind, colonyId, placeName, unrest));
        }
        return new TerritorySnapshotPayload(dimension, nations, plots);
    }

    public static void handle(TerritorySnapshotPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Map<UUID, TerritoryClientCache.NationView> nations = new HashMap<>();
            for (NationPart nation : payload.nations) {
                nations.put(nation.id, new TerritoryClientCache.NationView(nation.id, nation.name, nation.color));
            }
            Map<ChunkRef, TerritoryClientCache.Plot> plots = new HashMap<>();
            for (PlotPart plot : payload.plots) {
                if (plot.nationIndex < 0 || plot.nationIndex >= payload.nations.size()) {
                    continue;
                }
                UUID nation = payload.nations.get(plot.nationIndex).id;
                plots.put(
                        new ChunkRef(payload.dimension, plot.x, plot.z),
                        new TerritoryClientCache.Plot(nation, land(plot.kind), plot.colonyId, plot.placeName, plot.unrest));
            }
            TerritoryClientCache.replace(payload.dimension, nations, plots);
        });
    }

    private static TerritoryClientCache.Land land(byte kind) {
        return switch (kind) {
            case CITY -> TerritoryClientCache.Land.CITY;
            case CORE -> TerritoryClientCache.Land.CORE;
            default -> TerritoryClientCache.Land.STATE;
        };
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
