package com.mrzeyner.colonynations.territory;

import java.util.Optional;
import java.util.UUID;

/**
 * Where a nation may place a new town hall.
 * City land wins over state land. Wilderness is allowed only for a nation that has no cities yet.
 */
public final class FoundingRules {
    public enum Verdict {
        ALLOW,
        FOREIGN_STATE,
        CITY_LAND,
        NEEDS_STATE_LAND
    }

    private FoundingRules() {
    }

    public static Verdict check(boolean nationHasCities, UUID nationId, Optional<UUID> stateOwner, boolean cityLand) {
        if (cityLand) {
            return Verdict.CITY_LAND;
        }
        if (stateOwner.isPresent() && !stateOwner.get().equals(nationId)) {
            return Verdict.FOREIGN_STATE;
        }
        if (stateOwner.isPresent()) {
            return Verdict.ALLOW;
        }
        if (!nationHasCities) {
            return Verdict.ALLOW;
        }
        return Verdict.NEEDS_STATE_LAND;
    }
}
