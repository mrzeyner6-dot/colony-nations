package com.mrzeyner.colonynations.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SeparatismTest {
    @Test
    void lowHappinessGrowsAndHighHappinessDecays() {
        assertEquals(43, Separatism.advance(40, 4.5, 5.0, 6, 4, false));
        assertEquals(36, Separatism.advance(40, 5.0, 5.0, 6, 4, false));
        assertEquals(36, Separatism.advance(40, 8.0, 5.0, 6, 4, false));
        assertEquals(41, Separatism.advance(40, 4.9, 5.0, 6, 4, false));
    }

    @Test
    void scoreStaysInsideZeroAndOneHundred() {
        assertEquals(0, Separatism.advance(2, 9.0, 5.0, 6, 4, false));
        assertEquals(100, Separatism.advance(90, 0.0, 5.0, 6, 4, false));
        assertEquals(Separatism.Stage.INDEPENDENCE, Separatism.stage(100));
        assertEquals(Separatism.Stage.CALM, Separatism.stage(-4));
    }

    @Test
    void capitalDoesNotRiseAboveUnrest() {
        assertEquals(79, Separatism.advance(70, 0.0, 5.0, 6, 4, true));
        assertEquals(79, Separatism.advance(79, 1.0, 5.0, 6, 4, true));
        assertEquals(75, Separatism.advance(79, 6.0, 5.0, 6, 4, true));
        assertEquals(79, Separatism.advance(100, 9.0, 5.0, 6, 4, true));
        assertEquals(Separatism.Stage.UNREST, Separatism.stage(79));
    }

    @Test
    void upwardCrossingsOnly() {
        Separatism.Crossing intoUnrest = Separatism.crossing(40, 55);
        assertTrue(intoUnrest.unrest());
        assertFalse(intoUnrest.movement());
        assertFalse(intoUnrest.independence());

        Separatism.Crossing intoMovement = Separatism.crossing(60, 85);
        assertFalse(intoMovement.unrest());
        assertTrue(intoMovement.movement());

        Separatism.Crossing stay = Separatism.crossing(85, 90);
        assertFalse(stay.movement());

        Separatism.Crossing decay = Separatism.crossing(85, 40);
        assertFalse(decay.unrest());
        assertFalse(decay.movement());

        assertTrue(Separatism.crossing(90, 100).independence());
        assertFalse(Separatism.crossing(40, 100).movement());
        assertFalse(Separatism.crossing(40, 100).unrest());
    }

    @Test
    void mapBandChangesOnlyAtStageBoundaries() {
        assertEquals(Separatism.BAND_CALM, Separatism.mapBand(0));
        assertEquals(Separatism.BAND_CALM, Separatism.mapBand(49));
        assertEquals(Separatism.BAND_UNREST, Separatism.mapBand(50));
        assertEquals(Separatism.BAND_UNREST, Separatism.mapBand(79));
        assertEquals(Separatism.BAND_MOVEMENT, Separatism.mapBand(80));
        assertEquals(Separatism.BAND_MOVEMENT, Separatism.mapBand(99));
        assertEquals(Separatism.BAND_CALM, Separatism.mapBand(100));
    }
}
