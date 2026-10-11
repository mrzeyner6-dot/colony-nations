package com.mrzeyner.colonynations.compat.minecolonies;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.minecolonies.api.colony.IColony;

import com.mrzeyner.colonynations.Config;
import com.mrzeyner.colonynations.data.NationSavedData;
import com.mrzeyner.colonynations.economy.Separatism;
import com.mrzeyner.colonynations.nation.CityRef;
import com.mrzeyner.colonynations.nation.Nation;
import com.mrzeyner.colonynations.territory.ChunkRef;
import com.mrzeyner.colonynations.territory.FoundingRing;

import net.minecraft.server.MinecraftServer;

/**
 * Turns a colony claim into city land and annexes the free founding ring around it.
 */
public final class CityTerritory {
    private CityTerritory() {
    }

    public static void onCityJoined(MinecraftServer server, NationSavedData data, IColony colony, long gameTime) {
        CityRef city = MineColoniesCities.cityRef(colony);
        Nation nation = data.registry().byColony(city).orElse(null);
        if (nation == null) {
            return;
        }
        Set<ChunkRef> claim = ColonyClaimCache.get().refresh(colony, gameTime);
        boolean changed = releaseClaim(data, claim);
        List<ChunkRef> ring = annexRing(data, colony, nation, claim);
        changed |= !ring.isEmpty();
        if (changed) {
            data.setDirty();
        }
        String name = colony.getName() == null ? "" : colony.getName();
        TerritorySync.broadcastCity(
                server, nation, claim, colony.getID(), name, Separatism.mapBand(nation.separatism(city)));
        TerritorySync.broadcastState(server, data, nation, ring, gameTime);
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

    /**
     * Annexes {@code foundingRingChunks} around {@code claim}. Skips state land of any nation
     * and any colony or free-city claim. Founding and free-city buy share this path.
     */
    static List<ChunkRef> annexRing(NationSavedData data, IColony colony, Nation nation, Set<ChunkRef> claim) {
        Set<ChunkRef> ring = FoundingRing.around(claim, Config.FOUNDING_RING_CHUNKS.get());
        Set<ChunkRef> occupied = new HashSet<>();
        for (ChunkRef chunk : ring) {
            if (data.territory().owner(chunk).isPresent() || ColonyClaims.isCityLand(colony.getDimension(), chunk)) {
                occupied.add(chunk);
            }
        }
        List<ChunkRef> claimed = new ArrayList<>();
        for (ChunkRef chunk : FoundingRing.unowned(ring, occupied)) {
            data.territory().claim(chunk, nation.id());
            claimed.add(chunk);
        }
        return claimed;
    }
}
