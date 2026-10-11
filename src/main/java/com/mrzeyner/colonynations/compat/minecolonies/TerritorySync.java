package com.mrzeyner.colonynations.compat.minecolonies;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.minecolonies.api.colony.IColony;

import com.mrzeyner.colonynations.ColonyNations;
import com.mrzeyner.colonynations.Config;
import com.mrzeyner.colonynations.data.FreeCity;
import com.mrzeyner.colonynations.data.NationSavedData;
import com.mrzeyner.colonynations.economy.Separatism;
import com.mrzeyner.colonynations.nation.CityRef;
import com.mrzeyner.colonynations.nation.Nation;
import com.mrzeyner.colonynations.network.TerritoryDeltaPayload;
import com.mrzeyner.colonynations.network.TerritorySnapshotPayload;
import com.mrzeyner.colonynations.territory.ChunkRef;
import com.mrzeyner.colonynations.territory.NearestCity;
import com.mrzeyner.colonynations.territory.PlotLabels;

import net.minecraft.core.BlockPos;
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
 * City chunks carry the colony id and name. Core chunks carry the nearest city, computed here.
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
        Map<ChunkRef, PlotInfo> described = new LinkedHashMap<>();
        if (!data.unreadable()) {
            for (Nation nation : data.registry().all()) {
                CityFacts facts = facts(nation, dimension, gameTime);
                for (Map.Entry<ChunkRef, CityHit> entry : facts.hits.entrySet()) {
                    if (described.containsKey(entry.getKey())) {
                        continue;
                    }
                    CityHit hit = entry.getValue();
                    described.put(
                            entry.getKey(),
                            new PlotInfo(
                                    nation.id(),
                                    nation.name(),
                                    nation.color(),
                                    TerritorySnapshotPayload.CITY,
                                    hit.colonyId,
                                    hit.name,
                                    hit.unrest));
                    byId.putIfAbsent(nation.id(), nation);
                }
                Set<ChunkRef> state = new HashSet<>();
                for (ChunkRef chunk : data.territory().chunks(nation.id())) {
                    if (dimension.equals(chunk.dimension()) && !described.containsKey(chunk)) {
                        state.add(chunk);
                    }
                }
                Map<ChunkRef, PlotLabels.Label> labels = PlotLabels.ofState(
                        state, facts.claims, facts.anchors, facts.names, Config.FOUNDING_RING_CHUNKS.get());
                for (ChunkRef chunk : state) {
                    described.put(chunk, fromLabel(nation, labels.get(chunk)));
                    byId.putIfAbsent(nation.id(), nation);
                }
            }
        }
        if (!data.unreadable()) {
            for (FreeCity free : data.freeCities()) {
                if (!dimension.equals(free.city().dimension())) {
                    continue;
                }
                IColony colony = MineColoniesCities.findByCity(free.city());
                if (colony == null) {
                    continue;
                }
                UUID id = FreeCity.mapId(free.city());
                String name = free.name().isBlank() ? (colony.getName() == null ? "" : colony.getName()) : free.name();
                for (ChunkRef chunk : ColonyClaimCache.get().chunks(colony, gameTime)) {
                    if (!dimension.equals(chunk.dimension()) || described.containsKey(chunk)) {
                        continue;
                    }
                    described.put(
                            chunk,
                            new PlotInfo(id, name, FreeCity.GRAY, TerritorySnapshotPayload.CITY, colony.getID(), name, false));
                    byId.putIfAbsent(id, null);
                }
            }
        }
        Map<UUID, Integer> index = new HashMap<>();
        List<TerritorySnapshotPayload.NationPart> parts = new ArrayList<>();
        for (Map.Entry<UUID, Nation> entry : byId.entrySet()) {
            Nation nation = entry.getValue();
            if (nation == null) {
                continue;
            }
            index.put(nation.id(), parts.size());
            parts.add(new TerritorySnapshotPayload.NationPart(nation.id(), nation.name(), nation.color()));
        }
        for (Map.Entry<ChunkRef, PlotInfo> entry : described.entrySet()) {
            PlotInfo info = entry.getValue();
            if (index.containsKey(info.nationId())) {
                continue;
            }
            index.put(info.nationId(), parts.size());
            parts.add(new TerritorySnapshotPayload.NationPart(info.nationId(), info.nationName(), info.color()));
        }
        List<TerritorySnapshotPayload.PlotPart> plots = new ArrayList<>();
        for (Map.Entry<ChunkRef, PlotInfo> entry : described.entrySet()) {
            PlotInfo info = entry.getValue();
            Integer nationIndex = index.get(info.nationId());
            if (nationIndex == null) {
                continue;
            }
            ChunkRef chunk = entry.getKey();
            plots.add(new TerritorySnapshotPayload.PlotPart(
                    chunk.x(), chunk.z(), nationIndex, info.kind, info.colonyId, info.placeName, info.unrest));
        }
        PacketDistributor.sendToPlayer(player, new TerritorySnapshotPayload(dimension, parts, plots));
    }

    public static void stateClaim(MinecraftServer server, ChunkRef chunk, Nation nation) {
        broadcastState(server, NationSavedData.get(server), nation, List.of(chunk), server.overworld().getGameTime());
    }

    public static void broadcastCity(
            MinecraftServer server,
            Nation nation,
            Iterable<ChunkRef> chunks,
            int colonyId,
            String placeName,
            boolean unrest) {
        String name = placeName == null ? "" : placeName;
        Map<String, List<TerritoryDeltaPayload.Change>> byDimension = new HashMap<>();
        for (ChunkRef chunk : chunks) {
            byDimension.computeIfAbsent(chunk.dimension(), key -> new ArrayList<>())
                    .add(TerritoryDeltaPayload.Change.plot(
                            TerritoryDeltaPayload.CITY,
                            chunk.x(),
                            chunk.z(),
                            nation.id(),
                            nation.name(),
                            nation.color(),
                            colonyId,
                            name,
                            unrest));
        }
        send(server, byDimension);
    }

    /**
     * Claim chunks of a free city, drawn in gray under a synthetic id that is not in the nation registry.
     */
    public static void broadcastFreeCity(MinecraftServer server, CityRef city, String placeName, Iterable<ChunkRef> chunks) {
        UUID id = FreeCity.mapId(city);
        String name = placeName == null ? "" : placeName;
        Map<String, List<TerritoryDeltaPayload.Change>> byDimension = new HashMap<>();
        for (ChunkRef chunk : chunks) {
            byDimension.computeIfAbsent(chunk.dimension(), key -> new ArrayList<>())
                    .add(TerritoryDeltaPayload.Change.plot(
                            TerritoryDeltaPayload.CITY,
                            chunk.x(),
                            chunk.z(),
                            id,
                            name,
                            FreeCity.GRAY,
                            city.colonyId(),
                            name,
                            false));
        }
        send(server, byDimension);
    }

    public static void forgetNation(MinecraftServer server, UUID nationId) {
        sendToAll(server, new TerritoryDeltaPayload("", List.of(TerritoryDeltaPayload.Change.forget(nationId))));
    }

    /**
     * State chunks outside every founding ring are annexed land. Chunks inside a ring are core
     * and name the nearest city.
     */
    public static void broadcastState(
            MinecraftServer server,
            NationSavedData data,
            Nation nation,
            Iterable<ChunkRef> chunks,
            long gameTime) {
        if (data.unreadable()) {
            return;
        }
        Map<String, List<ChunkRef>> byDimension = new HashMap<>();
        for (ChunkRef chunk : chunks) {
            byDimension.computeIfAbsent(chunk.dimension(), key -> new ArrayList<>()).add(chunk);
        }
        for (Map.Entry<String, List<ChunkRef>> entry : byDimension.entrySet()) {
            delta(server, entry.getKey(), stateChanges(nation, entry.getKey(), entry.getValue(), gameTime));
        }
    }

    /**
     * Sends the current core-or-annexed label for every state chunk of the nation in one dimension.
     * Used after a claim or a colony rename, when chunks that were not themselves edited can change label.
     */
    public static void relabelState(MinecraftServer server, NationSavedData data, Nation nation, String dimension, long gameTime) {
        List<ChunkRef> state = new ArrayList<>();
        for (ChunkRef chunk : data.territory().chunks(nation.id())) {
            if (dimension.equals(chunk.dimension())) {
                state.add(chunk);
            }
        }
        broadcastState(server, data, nation, state, gameTime);
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
            Map<ChunkRef, CityOwner> cityOwners = cityOwners(data, entry.getKey(), gameTime);
            List<TerritoryDeltaPayload.Change> changes = new ArrayList<>();
            Map<UUID, List<ChunkRef>> stateChunks = new HashMap<>();
            Map<UUID, Nation> stateNations = new HashMap<>();
            for (ChunkRef chunk : entry.getValue()) {
                CityOwner city = cityOwners.get(chunk);
                if (city != null) {
                    changes.add(TerritoryDeltaPayload.Change.plot(
                            TerritoryDeltaPayload.CITY,
                            chunk.x(),
                            chunk.z(),
                            city.nation.id(),
                            city.nation.name(),
                            city.nation.color(),
                            city.colonyId,
                            city.name,
                            city.unrest));
                    continue;
                }
                Nation stateNation = data.territory().owner(chunk).flatMap(data.registry()::byId).orElse(null);
                if (stateNation == null) {
                    changes.add(TerritoryDeltaPayload.Change.release(chunk.x(), chunk.z()));
                    continue;
                }
                stateNations.put(stateNation.id(), stateNation);
                stateChunks.computeIfAbsent(stateNation.id(), key -> new ArrayList<>()).add(chunk);
            }
            for (Map.Entry<UUID, List<ChunkRef>> stateEntry : stateChunks.entrySet()) {
                changes.addAll(stateChanges(
                        stateNations.get(stateEntry.getKey()), entry.getKey(), stateEntry.getValue(), gameTime));
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

    private static List<TerritoryDeltaPayload.Change> stateChanges(
            Nation nation,
            String dimension,
            Collection<ChunkRef> chunks,
            long gameTime) {
        CityFacts facts = facts(nation, dimension, gameTime);
        Map<ChunkRef, PlotLabels.Label> labels = PlotLabels.ofState(
                Set.copyOf(chunks), facts.claims, facts.anchors, facts.names, Config.FOUNDING_RING_CHUNKS.get());
        List<TerritoryDeltaPayload.Change> changes = new ArrayList<>();
        for (ChunkRef chunk : chunks) {
            PlotInfo info = fromLabel(nation, labels.get(chunk));
            byte op = info.kind == TerritorySnapshotPayload.CORE ? TerritoryDeltaPayload.CORE : TerritoryDeltaPayload.STATE;
            changes.add(TerritoryDeltaPayload.Change.plot(
                    op,
                    chunk.x(),
                    chunk.z(),
                    nation.id(),
                    nation.name(),
                    nation.color(),
                    info.colonyId,
                    info.placeName,
                    info.unrest));
        }
        return changes;
    }

    private static PlotInfo fromLabel(Nation nation, PlotLabels.Label label) {
        if (label != null && label.kind() == PlotLabels.Kind.CORE) {
            int colonyId = label.city() == null ? -1 : label.city().colonyId();
            return new PlotInfo(
                    nation.id(), nation.name(), nation.color(), TerritorySnapshotPayload.CORE, colonyId, label.name(), false);
        }
        return new PlotInfo(nation.id(), nation.name(), nation.color(), TerritorySnapshotPayload.STATE, -1, "", false);
    }

    private static void sendToAll(MinecraftServer server, TerritoryDeltaPayload payload) {
        server.getPlayerList().broadcastAll(new ClientboundCustomPayloadPacket(payload));
    }

    private static Map<ChunkRef, CityOwner> cityOwners(NationSavedData data, String dimension, long gameTime) {
        Map<ChunkRef, CityOwner> owners = new HashMap<>();
        for (Nation nation : data.registry().all()) {
            CityFacts facts = facts(nation, dimension, gameTime);
            for (Map.Entry<ChunkRef, CityHit> entry : facts.hits.entrySet()) {
                CityHit hit = entry.getValue();
                owners.putIfAbsent(entry.getKey(), new CityOwner(nation, hit.colonyId, hit.name, hit.unrest));
            }
        }
        return owners;
    }

    private static CityFacts facts(Nation nation, String dimension, long gameTime) {
        List<Collection<ChunkRef>> claims = new ArrayList<>();
        List<NearestCity.Anchor> anchors = new ArrayList<>();
        Map<CityRef, String> names = new HashMap<>();
        Map<ChunkRef, CityHit> hits = new LinkedHashMap<>();
        for (CityRef ref : nation.cities()) {
            if (!dimension.equals(ref.dimension())) {
                continue;
            }
            IColony colony = MineColoniesCities.findByCity(ref);
            if (colony == null) {
                continue;
            }
            Set<ChunkRef> chunks = ColonyClaimCache.get().chunks(colony, gameTime);
            claims.add(chunks);
            BlockPos center = colony.getCenter();
            anchors.add(new NearestCity.Anchor(ref, center.getX() >> 4, center.getZ() >> 4));
            String name = colony.getName() == null ? "" : colony.getName();
            names.put(ref, name);
            boolean unrest = Separatism.unrest(nation.separatism(ref));
            for (ChunkRef chunk : chunks) {
                hits.putIfAbsent(chunk, new CityHit(colony.getID(), name, unrest));
            }
        }
        return new CityFacts(claims, anchors, names, hits);
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

    private record PlotInfo(UUID nationId, String nationName, int color, byte kind, int colonyId, String placeName, boolean unrest) {
    }

    private record CityHit(int colonyId, String name, boolean unrest) {
    }

    private record CityOwner(Nation nation, int colonyId, String name, boolean unrest) {
    }

    private record CityFacts(
            List<Collection<ChunkRef>> claims,
            List<NearestCity.Anchor> anchors,
            Map<CityRef, String> names,
            Map<ChunkRef, CityHit> hits) {
    }
}
