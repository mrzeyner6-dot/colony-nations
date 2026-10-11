package com.mrzeyner.colonynations.compat.minecolonies;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.claim.IChunkClaimData;

import com.mrzeyner.colonynations.nation.CityRef;
import com.mrzeyner.colonynations.nation.Nation;
import com.mrzeyner.colonynations.nation.NationRegistry;
import com.mrzeyner.colonynations.territory.ChunkRef;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * Reads MineColonies claim chunks. A chunk counts when the colony owns it or holds a static claim.
 * There is no claim-growth event on the MineColonies bus, so callers cache the result themselves.
 */
public final class ColonyClaimChunks {
    private ColonyClaimChunks() {
    }

    public static Set<ChunkRef> of(IColony colony) {
        ResourceKey<Level> dimension = colony.getDimension();
        Map<ChunkPos, IChunkClaimData> claims = IColonyManager.getInstance().getClaimData(dimension);
        Set<ChunkRef> chunks = new HashSet<>();
        if (claims != null) {
            String dimensionId = dimension.location().toString();
            int id = colony.getID();
            for (Map.Entry<ChunkPos, IChunkClaimData> entry : claims.entrySet()) {
                if (claimedBy(entry.getValue(), id)) {
                    ChunkPos pos = entry.getKey();
                    chunks.add(new ChunkRef(dimensionId, pos.x, pos.z));
                }
            }
        }
        if (chunks.isEmpty() && colony.getCenter() != null) {
            chunks.add(centerChunk(colony));
        }
        return Set.copyOf(chunks);
    }

    public static Set<ChunkRef> ofNation(Nation nation) {
        Set<ChunkRef> chunks = new HashSet<>();
        for (CityRef city : nation.cities()) {
            IColony colony = MineColoniesCities.findByCity(city);
            if (colony == null) {
                continue;
            }
            Set<ChunkRef> claim = of(colony);
            ColonyClaimCache.remember(city, claim);
            chunks.addAll(claim);
        }
        return chunks;
    }

    public static int townHallLevels(Nation nation) {
        int levels = 0;
        for (CityRef city : nation.cities()) {
            IColony colony = MineColoniesCities.findByCity(city);
            if (colony == null || !colony.getServerBuildingManager().hasTownHall()) {
                continue;
            }
            levels += colony.getServerBuildingManager().getTownHall().getBuildingLevel();
        }
        return levels;
    }

    /**
     * True when this chunk is claimed by a colony that already belongs to some nation.
     */
    public static boolean isNationCityLand(NationRegistry registry, ResourceKey<Level> dimension, ChunkPos pos) {
        IChunkClaimData data = IColonyManager.getInstance().getClaimData(dimension, pos);
        if (data == null) {
            return false;
        }
        String dimensionId = dimension.location().toString();
        if (belongsToNation(registry, dimensionId, data.getOwningColony())) {
            return true;
        }
        for (Integer id : data.getStaticClaimColonies()) {
            if (id != null && belongsToNation(registry, dimensionId, id)) {
                return true;
            }
        }
        return false;
    }

    private static boolean claimedBy(IChunkClaimData data, int colonyId) {
        if (data == null) {
            return false;
        }
        if (data.getOwningColony() == colonyId) {
            return true;
        }
        return data.getStaticClaimColonies().contains(colonyId);
    }

    private static boolean belongsToNation(NationRegistry registry, String dimension, int colonyId) {
        if (colonyId <= 0) {
            return false;
        }
        return registry.byColony(new CityRef(dimension, colonyId)).isPresent();
    }

    private static ChunkRef centerChunk(IColony colony) {
        BlockPos center = colony.getCenter();
        return new ChunkRef(
                colony.getDimension().location().toString(),
                center.getX() >> 4,
                center.getZ() >> 4);
    }
}
