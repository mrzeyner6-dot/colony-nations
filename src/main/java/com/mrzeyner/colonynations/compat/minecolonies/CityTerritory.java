package com.mrzeyner.colonynations.compat.minecolonies;

import java.util.Set;

import com.minecolonies.api.colony.IColony;

import com.mrzeyner.colonynations.Config;
import com.mrzeyner.colonynations.data.NationSavedData;
import com.mrzeyner.colonynations.nation.CityRef;
import com.mrzeyner.colonynations.nation.Nation;
import com.mrzeyner.colonynations.territory.ChunkRef;
import com.mrzeyner.colonynations.territory.FoundingRing;

/**
 * Turns a colony claim into city land and annexes the free founding ring around it.
 */
public final class CityTerritory {
    private CityTerritory() {
    }

    public static void onCityJoined(NationSavedData data, IColony colony, long gameTime) {
        CityRef city = MineColoniesCities.cityRef(colony);
        Nation nation = data.registry().byColony(city).orElse(null);
        if (nation == null) {
            return;
        }
        Set<ChunkRef> claim = ColonyClaimCache.get().refresh(colony, gameTime);
        boolean changed = releaseClaim(data, claim);
        changed |= annexRing(data, colony, nation, claim);
        if (changed) {
            data.setDirty();
        }
    }

    /**
     * City land wins over state land, including after the claim grows.
     */
    public static boolean releaseClaim(NationSavedData data, Set<ChunkRef> claim) {
        boolean changed = false;
        for (ChunkRef chunk : claim) {
            if (data.territory().owner(chunk).isPresent()) {
                data.territory().release(chunk);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean annexRing(NationSavedData data, IColony colony, Nation nation, Set<ChunkRef> claim) {
        boolean changed = false;
        for (ChunkRef chunk : FoundingRing.around(claim, Config.FOUNDING_RING_CHUNKS.get())) {
            if (data.territory().owner(chunk).isPresent()) {
                continue;
            }
            if (ColonyClaims.isCityLand(colony.getDimension(), chunk)) {
                continue;
            }
            data.territory().claim(chunk, nation.id());
            changed = true;
        }
        return changed;
    }
}
