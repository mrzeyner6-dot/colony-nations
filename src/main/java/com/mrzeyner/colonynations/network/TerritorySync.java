package com.mrzeyner.colonynations.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mrzeyner.colonynations.ColonyNations;
import com.mrzeyner.colonynations.client.TerritoryClientCache;
import com.mrzeyner.colonynations.compat.minecolonies.ColonyClaimChunks;
import com.mrzeyner.colonynations.data.NationSavedData;
import com.mrzeyner.colonynations.nation.Nation;
import com.mrzeyner.colonynations.territory.ChunkRef;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Sends territory to clients: a full snapshot on join and on dimension change, then only deltas.
 * Chunk packets stay inside the dimension they describe.
 */
@EventBusSubscriber(modid = ColonyNations.MOD_ID)
public final class TerritorySync {
    private TerritorySync() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(TerritorySnapshotPayload.TYPE, TerritorySnapshotPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> TerritoryClientCache.replace(context.player().level().dimension().location().toString(), payload)));
        registrar.playToClient(TerritoryDeltaPayload.TYPE, TerritoryDeltaPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> TerritoryClientCache.apply(payload)));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendSnapshot(player);
        }
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.getServer() == null) {
            return;
        }
        ServerLevel level = player.getServer().getLevel(event.getTo());
        if (level != null) {
            sendSnapshot(player, level);
        }
    }

    public static void sendSnapshot(ServerPlayer player) {
        if (player.level() instanceof ServerLevel level) {
            sendSnapshot(player, level);
        }
    }

    public static void nationChanged(MinecraftServer server, Nation nation) {
        sendAll(server, new TerritoryDeltaPayload(List.of(stamp(nation)), List.of(), List.of()));
    }

    public static void nationRemoved(MinecraftServer server, UUID nationId) {
        sendAll(server, new TerritoryDeltaPayload(List.of(), List.of(nationId), List.of()));
    }

    /**
     * Diff of one nation's state-land chunks. City land is sent separately.
     */
    public static void stateChanged(MinecraftServer server, UUID nationId, Set<ChunkRef> before, Set<ChunkRef> after) {
        Map<String, List<ChunkPlot>> byDimension = new HashMap<>();
        for (ChunkRef chunk : before) {
            if (!after.contains(chunk)) {
                byDimension.computeIfAbsent(chunk.dimension(), key -> new ArrayList<>())
                        .add(ChunkPlot.release(chunk.x(), chunk.z()));
            }
        }
        for (ChunkRef chunk : after) {
            if (!before.contains(chunk)) {
                byDimension.computeIfAbsent(chunk.dimension(), key -> new ArrayList<>())
                        .add(ChunkPlot.state(chunk.x(), chunk.z(), nationId));
            }
        }
        byDimension.forEach((dimension, plots) -> sendDimension(server, dimension, plots));
    }

    public static void cityChanged(MinecraftServer server, UUID nationId, Set<ChunkRef> chunks, boolean added, String cityName) {
        Map<String, List<ChunkPlot>> byDimension = new HashMap<>();
        for (ChunkRef chunk : chunks) {
            ChunkPlot plot = added
                    ? ChunkPlot.city(chunk.x(), chunk.z(), nationId, cityName)
                    : ChunkPlot.release(chunk.x(), chunk.z());
            byDimension.computeIfAbsent(chunk.dimension(), key -> new ArrayList<>()).add(plot);
        }
        byDimension.forEach((dimension, plots) -> sendDimension(server, dimension, plots));
    }

    private static void sendSnapshot(ServerPlayer player, ServerLevel level) {
        NationSavedData data = NationSavedData.get(level.getServer());
        String dimension = level.dimension().location().toString();
        List<NationStamp> nations = new ArrayList<>();
        Set<ChunkRef> city = new HashSet<>();
        List<ChunkPlot> plots = new ArrayList<>();
        for (Nation nation : data.registry().all()) {
            nations.add(stamp(nation));
            for (Map.Entry<ChunkRef, String> entry : ColonyClaimChunks.namedClaims(nation).entrySet()) {
                ChunkRef chunk = entry.getKey();
                if (!dimension.equals(chunk.dimension())) {
                    continue;
                }
                city.add(chunk);
                plots.add(ChunkPlot.city(chunk.x(), chunk.z(), nation.id(), entry.getValue()));
            }
        }
        for (ChunkRef chunk : data.territory().allChunks()) {
            if (!dimension.equals(chunk.dimension()) || city.contains(chunk)) {
                continue;
            }
            UUID owner = data.territory().owner(chunk).orElse(null);
            if (owner != null) {
                plots.add(ChunkPlot.state(chunk.x(), chunk.z(), owner));
            }
        }
        PacketDistributor.sendToPlayer(player, new TerritorySnapshotPayload(nations, plots));
    }

    private static void sendDimension(MinecraftServer server, String dimension, List<ChunkPlot> plots) {
        if (plots.isEmpty()) {
            return;
        }
        ResourceLocation id = ResourceLocation.parse(dimension);
        ServerLevel level = server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, id));
        if (level == null) {
            return;
        }
        PacketDistributor.sendToPlayersInDimension(level, new TerritoryDeltaPayload(List.of(), List.of(), plots));
    }

    private static void sendAll(MinecraftServer server, TerritoryDeltaPayload payload) {
        if (payload.empty()) {
            return;
        }
        PacketDistributor.sendToAllPlayers(payload);
    }

    private static NationStamp stamp(Nation nation) {
        return new NationStamp(nation.id(), nation.name(), nation.color());
    }
}
