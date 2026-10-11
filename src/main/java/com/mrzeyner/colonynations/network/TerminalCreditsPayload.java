package com.mrzeyner.colonynations.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mrzeyner.colonynations.ColonyNations;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Buyer credits for the owner window. An empty name means the unnamed counter used until a train is bound.
 */
public record TerminalCreditsPayload(BlockPos pos, List<Line> lines) implements CustomPacketPayload {
    public static final int MAX_LINES = 16;

    public static final Type<TerminalCreditsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ColonyNations.MOD_ID, "terminal_credits"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalCreditsPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBlockPos(payload.pos);
                int size = Math.min(MAX_LINES, payload.lines.size());
                buf.writeVarInt(size);
                for (int index = 0; index < size; index++) {
                    Line line = payload.lines.get(index);
                    buf.writeUUID(line.buyer());
                    buf.writeUtf(line.name() == null ? "" : line.name(), MenuLimits.LABEL);
                    buf.writeVarLong(line.credit());
                }
            },
            buf -> {
                BlockPos pos = buf.readBlockPos();
                int size = Math.min(MAX_LINES, buf.readVarInt());
                List<Line> lines = new ArrayList<>();
                for (int index = 0; index < size; index++) {
                    lines.add(new Line(buf.readUUID(), buf.readUtf(MenuLimits.LABEL), buf.readVarLong()));
                }
                return new TerminalCreditsPayload(pos, List.copyOf(lines));
            });

    public record Line(UUID buyer, String name, long credit) {
        public Line {
            name = name == null ? "" : name;
        }
    }

    public TerminalCreditsPayload {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
