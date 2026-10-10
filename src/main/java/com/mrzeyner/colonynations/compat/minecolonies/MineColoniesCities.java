package com.mrzeyner.colonynations.compat.minecolonies;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;

import com.mrzeyner.colonynations.nation.CityRef;
import com.mrzeyner.colonynations.nation.Nation;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Turns a MineColonies colony into a {@link CityRef}. The player types only the colony id;
 * the dimension comes from the colony.
 */
public final class MineColoniesCities {
    private MineColoniesCities() {
    }

    public static CityRef cityRef(IColony colony) {
        return new CityRef(colony.getDimension().location().toString(), colony.getID());
    }

    /**
     * Colonies with this id owned by {@code owner}. When several match, the one in {@code prefer} wins.
     * Returns null when none match, or when several match and none is the preferred dimension.
     */
    public static IColony findOwned(int colonyId, UUID owner, ResourceKey<Level> prefer) {
        List<IColony> matches = new ArrayList<>();
        for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
            if (colony.getID() == colonyId && owner.equals(colony.getPermissions().getOwner())) {
                matches.add(colony);
            }
        }
        return pick(matches, prefer);
    }

    public static IColony findByCity(CityRef city) {
        for (IColony colony : IMinecoloniesAPI.getInstance().getColonyManager().getAllColonies()) {
            if (city.equals(cityRef(colony))) {
                return colony;
            }
        }
        return null;
    }

    /**
     * Member city with this colony id. A single member matches immediately.
     * Several members resolve to the one in {@code prefer}.
     */
    public static CityRef member(Nation nation, int colonyId, ResourceKey<Level> prefer) {
        List<CityRef> matches = new ArrayList<>();
        for (CityRef city : nation.cities()) {
            if (city.colonyId() == colonyId) {
                matches.add(city);
            }
        }
        if (matches.size() == 1) {
            return matches.get(0);
        }
        if (prefer == null) {
            return null;
        }
        String dimension = prefer.location().toString();
        CityRef found = null;
        for (CityRef city : matches) {
            if (!dimension.equals(city.dimension())) {
                continue;
            }
            if (found != null) {
                return null;
            }
            found = city;
        }
        return found;
    }

    private static IColony pick(List<IColony> matches, ResourceKey<Level> prefer) {
        if (matches.size() == 1) {
            return matches.get(0);
        }
        if (prefer == null) {
            return null;
        }
        IColony found = null;
        for (IColony colony : matches) {
            if (!prefer.equals(colony.getDimension())) {
                continue;
            }
            if (found != null) {
                return null;
            }
            found = colony;
        }
        return found;
    }
}
