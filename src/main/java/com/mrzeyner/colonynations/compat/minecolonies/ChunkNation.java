package com.mrzeyner.colonynations.compat.minecolonies;

import java.util.List;
import java.util.UUID;

import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.claim.IChunkClaimData;
import com.minecolonies.api.util.constant.ColonyManagerConstants;

import com.mrzeyner.colonynations.data.NationSavedData;
import com.mrzeyner.colonynations.nation.CityRef;
import com.mrzeyner.colonynations.nation.Nation;
import com.mrzeyner.colonynations.territory.ChunkRef;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/**
 * Nation that currently owns a chunk. City land wins over state land.
 * A free-city claim is city land with no nation.
 */
public final class ChunkNation {
    private ChunkNation() {
    }

    public static UUID of(ServerLevel level, BlockPos pos) {
        NationSavedData data = NationSavedData.get(level.getServer());
        if (data.unreadable()) {
            return null;
        }
        String dimension = level.dimension().location().toString();
        ChunkRef chunk = new ChunkRef(dimension, pos.getX() >> 4, pos.getZ() >> 4);
        IChunkClaimData claim = IColonyManager.getInstance().getClaimData(level.dimension(), new ChunkPos(chunk.x(), chunk.z()));
        if (claim != null && claim.getOwningColony() != ColonyManagerConstants.NO_COLONY_ID) {
            return nationOf(data, dimension, claim.getOwningColony());
        }
        if (claim != null) {
            List<Integer> staticClaims = claim.getStaticClaimColonies();
            if (staticClaims != null) {
                for (int colonyId : staticClaims) {
                    UUID nation = nationOf(data, dimension, colonyId);
                    if (nation != null) {
                        return nation;
                    }
                }
            }
        }
        if (ColonyClaims.isCityLand(level.dimension(), chunk)) {
            return null;
        }
        return data.territory().owner(chunk).orElse(null);
    }

    private static UUID nationOf(NationSavedData data, String dimension, int colonyId) {
        return data.registry().byColony(new CityRef(dimension, colonyId)).map(Nation::id).orElse(null);
    }
}
