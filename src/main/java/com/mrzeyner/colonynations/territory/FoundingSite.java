package com.mrzeyner.colonynations.territory;

/**
 * Where a nation owner may place a new town hall.
 * The first city has to stand on unowned land. Later cities have to stand on that nation's state land.
 */
public final class FoundingSite {
    public enum Verdict {
        ALLOW,
        NEED_WILDERNESS,
        NEED_STATE_LAND,
        FOREIGN
    }

    private FoundingSite() {
    }

    public static Verdict decide(boolean nationHasCities, boolean ownStateLand, boolean foreignStateLand, boolean cityLand) {
        if (foreignStateLand || (cityLand && !nationHasCities)) {
            return Verdict.FOREIGN;
        }
        if (!nationHasCities) {
            return ownStateLand ? Verdict.NEED_WILDERNESS : Verdict.ALLOW;
        }
        if (ownStateLand && !cityLand) {
            return Verdict.ALLOW;
        }
        if (cityLand && !ownStateLand) {
            return Verdict.FOREIGN;
        }
        return Verdict.NEED_STATE_LAND;
    }
}
