package com.mrzeyner.colonynations.territory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FoundingSiteTest {
    @Test
    void firstCityMustBeUnowned() {
        assertEquals(FoundingSite.Verdict.ALLOW, FoundingSite.decide(false, false, false, false));
        assertEquals(FoundingSite.Verdict.NEED_WILDERNESS, FoundingSite.decide(false, true, false, false));
        assertEquals(FoundingSite.Verdict.FOREIGN, FoundingSite.decide(false, false, true, false));
        assertEquals(FoundingSite.Verdict.FOREIGN, FoundingSite.decide(false, false, false, true));
    }

    @Test
    void laterCityMustBeOwnStateLand() {
        assertEquals(FoundingSite.Verdict.ALLOW, FoundingSite.decide(true, true, false, false));
        assertEquals(FoundingSite.Verdict.NEED_STATE_LAND, FoundingSite.decide(true, false, false, false));
        assertEquals(FoundingSite.Verdict.NEED_STATE_LAND, FoundingSite.decide(true, true, false, true));
        assertEquals(FoundingSite.Verdict.FOREIGN, FoundingSite.decide(true, false, true, false));
        assertEquals(FoundingSite.Verdict.FOREIGN, FoundingSite.decide(true, false, false, true));
    }
}
