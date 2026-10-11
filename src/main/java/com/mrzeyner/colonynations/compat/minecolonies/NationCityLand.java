package com.mrzeyner.colonynations.compat.minecolonies;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.minecolonies.api.colony.IColony;

import com.mrzeyner.colonynations.Config;
import com.mrzeyner.colonynations.data.NationSavedData;
import com.mrzeyner.colonynations.nation.CityRef;
import com.mrzeyner.colonynations.nation.Nation;
import com.mrzeyner.colonynations.territory.ChunkRef;
import com.mrzeyner.colonynations.territory.FoundingRing;

/**
 * Turns a newly joined city's claim into city land and annexes the free ring around it.
 */
public final class NationCityLand {
    private NationCityLand() {
    }

    public static void grant(NationSavedData data, UUID nationId, IColony colony) {
        CityRef city = MineColoniesCities.cityRef(colony);
        Set<ChunkRef> claim = ColonyClaimChunks.of(colony);
        ColonyClaimCache.remember(city, claim);
        Set<ChunkRef> otherCityLand = new HashSet<>();
        for (Nation nation : data.registry().all()) {
            for (CityRef member : nation.cities()) {
                if (member.equals(city)) {
                    continue;
                }
                IColony other = MineColoniesCities.findByCity(member);
                if (other == null) {
                    continue;
                }
                Set<ChunkRef> chunks = ColonyClaimChunks.of(other);
                ColonyClaimCache.remember(member, chunks);
                otherCityLand.addAll(chunks);
            }
        }
        FoundingRing.grant(data.territory(), nationId, claim, otherCityLand, Config.FOUNDING_RING_CHUNKS.get());
        data.setDirty();
    }
}
