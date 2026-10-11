package com.mrzeyner.colonynations.compat.minecolonies;

import com.minecolonies.api.colony.IColony;

/**
 * Adult citizens of a colony. Children do not pay tax.
 */
public final class ColonyPopulation {
    private ColonyPopulation() {
    }

    public static int adults(IColony colony) {
        int count = 0;
        for (var citizen : colony.getCitizenManager().getCitizens()) {
            if (!citizen.isChild()) {
                count++;
            }
        }
        return count;
    }
}
