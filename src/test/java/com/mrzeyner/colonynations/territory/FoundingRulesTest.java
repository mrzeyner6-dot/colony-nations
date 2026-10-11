package com.mrzeyner.colonynations.territory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class FoundingRulesTest {
    private final UUID rome = UUID.randomUUID();
    private final UUID gaul = UUID.randomUUID();

    @Test
    void firstCityMayUseWildernessOrOwnLand() {
        assertEquals(FoundingRules.Verdict.ALLOW, FoundingRules.check(false, rome, Optional.empty(), false));
        assertEquals(FoundingRules.Verdict.ALLOW, FoundingRules.check(false, rome, Optional.of(rome), false));
        assertEquals(FoundingRules.Verdict.ALLOW, FoundingRules.check(true, rome, Optional.of(rome), false));
    }

    @Test
    void foreignStateAndCityLandAreRefused() {
        assertEquals(FoundingRules.Verdict.FOREIGN_STATE, FoundingRules.check(false, rome, Optional.of(gaul), false));
        assertEquals(FoundingRules.Verdict.FOREIGN_STATE, FoundingRules.check(true, rome, Optional.of(gaul), false));
        assertEquals(FoundingRules.Verdict.CITY_LAND, FoundingRules.check(false, rome, Optional.empty(), true));
        assertEquals(FoundingRules.Verdict.CITY_LAND, FoundingRules.check(true, rome, Optional.of(rome), true));
    }

    @Test
    void laterCitiesNeedOwnStateLand() {
        assertEquals(FoundingRules.Verdict.NEEDS_STATE_LAND, FoundingRules.check(true, rome, Optional.empty(), false));
    }
}
