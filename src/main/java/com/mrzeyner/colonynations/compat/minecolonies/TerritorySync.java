package com.mrzeyner.colonynations.compat.minecolonies;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.minecolonies.api.colony.IColony;

import com.mrzeyner.colonynations.ColonyNations;
import com.mrzeyner.colonynations.data.NationSavedData;
import com.mrzeyner.colonynations.nation.CityRef;
import com.mrzeyner.colonynations.nation.Nation;
import com.mrzeyner.colonynations.network.TerritoryDeltaPayload;
import com.mrzeyner.colonynations.network.TerritorySnapshotPayload;
import com.mrzeyner.colonynations.territory.ChunkRef;

import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Sends territory to players in the affected dimension only.
 * A login or dimension change replaces that dimension. Later edits are deltas.
 */
@EventBusSubscriber(modid = ColonyNations.MOD_ID)
public final class TerritorySync {
    private TerritorySync() {
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            snapshot(player);
        }
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            snapshot(player);
        }
    }

    public static void snapshot(ServerPlayer player) {
        MinecraftServer server = player.server;
        NationSavedData data = NationSavedData.get(server);
        String dimension = player.level().dimension().location().toString();
        long gameTime = server.overworld().getGameTime();
        Map<UUID, Nation> byId = new LinkedHashMap<>();
        Map<ChunkRef, Boolean> city = new LinkedHashMap<>();
        Map<ChunkRef, UUID> owners = new HashMap<>();
        if (!data.unreadable()) {
            for (Nation nation : data.registry().all()) {
                for (CityRef ref : nation.cities()) {
                    if (!dimension.equals(ref.dimension())) {
                        continue;
                    }
                    IColony colony = MineColoniesCities.findByCity(ref);
                    if (colony == null) {
                        continue;
                    }
                    for (ChunkRef chunk : ColonyClaimCache.get().chunks(colony, gameTime)) {
                        if (city.putIfAbsent(chunk, true) == null) {
                            owners.put(chunk, nation.id());
                            byId.putIfAbsent(nation.id(), nation);
                        }
                    }
                }
                for (ChunkRef chunk : data.territory().chunks(nation.id())) {
                    if (!dimension.equals(chunk.dimension()) || city.containsKey(chunk)) {
                        continue;
                    }
                    city.put(chunk, false);
                    owners.put(chunk, nation.id());
                    byId.putIfAbsent(nation.id(), nation);
                }
            }
        }
        List<Nation> nations = new ArrayList<>(byId.values());
        Map<UUID, Integer> index = new HashMap<>();
        List<TerritorySnapshotPayload.NationPart> parts = new ArrayList<>();
        for (int i = 0; i < nations.size(); i++) {
            Nation nation = nations.get(i);
            index.put(nation.id(), i);
            parts.add(new TerritorySnapshotPayload.NationPart(nation.id(), nation.name(), nation.color()));
        }
        List<TerritorySnapshotPayload.PlotPart> plots = new ArrayList<>();
        for (Map.Entry<ChunkRef, Boolean> entry : city.entrySet()) {
            ChunkRef chunk = entry.getKey();
            UUID owner = owners.get(chunk);
            if (owner == null || !index.containsKey(owner)) {
                continue;
            }
            plots.add(new TerritorySnapshotPayload.PlotPart(chunk.x(), chunk.z(), index.get(owner), entry.getValue()));
        }
        PacketDistributor.sendToPlayer(player, new TerritorySnapshotPayload(dimension, parts, plots));
    }

    public static void stateClaim(MinecraftServer server, ChunkRef chunk, Nation nation) {
        broadcastPlots(server, TerritoryDeltaPayload.STATE, nation, List.of(chunk));
    }

    public static void broadcastPlots(MinecraftServer server, byte op, Nation nation, Iterable<ChunkRef> chunks) {
        Map<String, List<TerritoryDeltaPayload.Change>> byDimension = new HashMap<>();
        for (ChunkRef chunk : chunks) {
            byDimension.computeIfAbsent(chunk.dimension(), key -> new ArrayList<>())
                    .add(TerritoryDeltaPayload.Change.plot(op, chunk.x(), chunk.z(), nation.id(), nation.name(), nation.color()));
        }
        send(server, byDimension);
    }

    public static void release(MinecraftServer server, ChunkRef chunk) {
        delta(server, chunk.dimension(), List.of(TerritoryDeltaPayload.Change.release(chunk.x(), chunk.z())));
    }

    public static void releaseAll(MinecraftServer server, Iterable<ChunkRef> chunks) {
        Map<String, List<TerritoryDeltaPayload.Change>> byDimension = new HashMap<>();
        for (ChunkRef chunk : chunks) {
            byDimension.computeIfAbsent(chunk.dimension(), key -> new ArrayList<>())
                    .add(TerritoryDeltaPayload.Change.release(chunk.x(), chunk.z()));
        }
        send(server, byDimension);
    }

    /**
     * Chunks that left a city claim. City land of a remaining colony wins, then state land, otherwise the plot is dropped.
     */
    public static void reconcile(MinecraftServer server, NationSavedData data, Iterable<ChunkRef> chunks, long gameTime) {
        Map<String, List<ChunkRef>> byDimension = new HashMap<>();
        for (ChunkRef chunk : chunks) {
            byDimension.computeIfAbsent(chunk.dimension(), key -> new ArrayList<>()).add(chunk);
        }
        for (Map.Entry<String, List<ChunkRef>> entry : byDimension.entrySet()) {
            Map<ChunkRef, Nation> cityOwners = cityOwners(data, entry.getKey(), gameTime);
            List<TerritoryDeltaPayload.Change> changes = new ArrayList<>();
            for (ChunkRef chunk : entry.getValue()) {
                Nation cityNation = cityOwners.get(chunk);
                if (cityNation != null) {
                    changes.add(TerritoryDeltaPayload.Change.plot(
                            TerritoryDeltaPayload.CITY, chunk.x(), chunk.z(), cityNation.id(), cityNation.name(), cityNation.color()));
                    continue;
                }
                Nation stateNation = data.territory().owner(chunk).flatMap(data.registry()::byId).orElse(null);
                if (stateNation != null) {
                    changes.add(TerritoryDeltaPayload.Change.plot(
                            TerritoryDeltaPayload.STATE, chunk.x(), chunk.z(), stateNation.id(), stateNation.name(), stateNation.color()));
                    continue;
                }
                changes.add(TerritoryDeltaPayload.Change.release(chunk.x(), chunk.z()));
            }
            delta(server, entry.getKey(), changes);
        }
    }

    public static void onCityLeft(MinecraftServer server, NationSavedData data, IColony colony, long gameTime) {
        Set<ChunkRef> claims = new HashSet<>(ColonyClaimCache.get().chunks(colony, gameTime));
        ColonyClaimCache.get().invalidate(MineColoniesCities.cityRef(colony));
        reconcile(server, data, claims, gameTime);
    }

    public static void onDisband(MinecraftServer server, Nation nation) {
        for (CityRef city : nation.cities()) {
            ColonyClaimCache.get().invalidate(city);
        }
        sendToAll(server, new TerritoryDeltaPayload("", List.of(TerritoryDeltaPayload.Change.forget(nation.id()))));
    }

    public static void nationMeta(MinecraftServer server, Nation nation) {
        sendToAll(server, new TerritoryDeltaPayload(
                "",
                List.of(TerritoryDeltaPayload.Change.nation(nation.id(), nation.name(), nation.color()))));
    }

    private static void sendToAll(MinecraftServer server, TerritoryDeltaPayload payload) {
        server.getPlayerList().broadcastAll(new ClientboundCustomPayloadPacket(payload));
    }

    private static Map<ChunkRef, Nation> cityOwners(NationSavedData data, String dimension, long gameTime) {
        Map<ChunkRef, Nation> owners = new HashMap<>();
        for (Nation nation : data.registry().all()) {
            for (CityRef ref : nation.cities()) {
                if (!dimension.equals(ref.dimension())) {
                    continue;
                }
                IColony colony = MineColoniesCities.findByCity(ref);
                if (colony == null) {
                    continue;
                }
                for (ChunkRef chunk : ColonyClaimCache.get().chunks(colony, gameTime)) {
                    owners.putIfAbsent(chunk, nation);
                }
            }
        }
        return owners;
    }

    private static void send(MinecraftServer server, Map<String, List<TerritoryDeltaPayload.Change>> byDimension) {
        for (Map.Entry<String, List<TerritoryDeltaPayload.Change>> entry : byDimension.entrySet()) {
            delta(server, entry.getKey(), entry.getValue());
        }
    }

    private static void delta(MinecraftServer server, String dimension, List<TerritoryDeltaPayload.Change> changes) {
        if (changes.isEmpty()) {
            return;
        }
        TerritoryDeltaPayload payload = new TerritoryDeltaPayload(dimension, changes);
        for (ServerLevel level : server.getAllLevels()) {
            if (dimension.equals(level.dimension().location().toString())) {
                PacketDistributor.sendToPlayersInDimension(level, payload);
            }
        }
    }
}
