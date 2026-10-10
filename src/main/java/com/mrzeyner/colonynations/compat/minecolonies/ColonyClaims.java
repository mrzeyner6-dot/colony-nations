package com.mrzeyner.colonynations.compat.minecolonies;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.claim.IChunkClaimData;
import com.minecolonies.api.util.constant.ColonyManagerConstants;

import com.mrzeyner.colonynations.territory.ChunkRef;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * MineColonies claim chunks for one colony. There is no claim-growth event, so callers cache this.
 */
public final class ColonyClaims {
    private ColonyClaims() {
    }

    public static Set<ChunkRef> of(IColony colony) {
        ResourceKey<Level> dimension = colony.getDimension();
        Map<ChunkPos, IChunkClaimData> claims = IColonyManager.getInstance().getClaimData(dimension);
        if (claims == null || claims.isEmpty()) {
            return Set.of();
        }
        int id = colony.getID();
        String dimensionId = dimension.location().toString();
        Set<ChunkRef> chunks = new HashSet<>();
        for (Map.Entry<ChunkPos, IChunkClaimData> entry : claims.entrySet()) {
            if (!ownedBy(entry.getValue(), id)) {
                continue;
            }
            ChunkPos pos = entry.getKey();
            chunks.add(new ChunkRef(dimensionId, pos.x, pos.z));
        }
        return Set.copyOf(chunks);
    }

    public static boolean isCityLand(ResourceKey<Level> dimension, ChunkRef chunk) {
        if (!dimension.location().toString().equals(chunk.dimension())) {
            return false;
        }
        IChunkClaimData data = IColonyManager.getInstance().getClaimData(dimension, new ChunkPos(chunk.x(), chunk.z()));
        if (data == null) {
            return false;
        }
        if (data.getOwningColony() != ColonyManagerConstants.NO_COLONY_ID) {
            return true;
        }
        List<Integer> staticClaims = data.getStaticClaimColonies();
        return staticClaims != null && !staticClaims.isEmpty();
    }

    private static boolean ownedBy(IChunkClaimData data, int colonyId) {
        if (data == null) {
            return false;
        }
        if (data.getOwningColony() == colonyId) {
            return true;
        }
        List<Integer> staticClaims = data.getStaticClaimColonies();
        return staticClaims != null && staticClaims.contains(colonyId);
    }
}
