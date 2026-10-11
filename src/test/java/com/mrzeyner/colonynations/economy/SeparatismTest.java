package com.mrzeyner.colonynations.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SeparatismTest {
    private static int step(int current, double happiness, double burden, boolean capital, boolean grace) {
        return Separatism.step(current, happiness, 5.0, 2.5, 6, 4, burden, capital, grace);
    }

    @Test
    void neutralTaxDoesNotGrowAndHighTaxDoes() {
        assertEquals(0.0d, Separatism.burden(0, 2, 6));
        assertEquals(1.0d, Separatism.burden(6, 2, 6));
        assertEquals(36, step(40, 3.0, 0.0, false, false));
        assertEquals(52, step(40, 3.0, 1.0, false, false));
        assertEquals(Separatism.Cause.CALM, Separatism.cause(3.0, 5.0, 2.5, 0, 2, 0));
        assertEquals(Separatism.Cause.TAX, Separatism.cause(3.0, 5.0, 2.5, 6, 2, 0));
    }

    @Test
    void collapseGrowsSlowerThanFullTaxPressure() {
        assertEquals(49, Separatism.step(40, 2.0, 5.0, 2.5, 6, 4, 0.0, false, false));
        assertEquals(58, Separatism.step(40, 2.0, 5.0, 0.0, 6, 4, 1.0, false, false));
        assertEquals(Separatism.Cause.COLLAPSE, Separatism.cause(2.0, 5.0, 2.5, 0, 2, 0));
    }

    @Test
    void graceBlocksGrowthAndStillDecays() {
        assertEquals(7, Separatism.daysLeft(10L, 10L, 7));
        assertEquals(1, Separatism.daysLeft(10L, 16L, 7));
        assertEquals(0, Separatism.daysLeft(10L, 17L, 7));
        assertEquals(40, step(40, 3.0, 1.0, false, true));
        assertEquals(36, step(40, 8.0, 1.0, false, true));
        assertEquals(Separatism.Cause.GRACE, Separatism.cause(2.0, 5.0, 2.5, 6, 2, 3));
    }

    @Test
    void scoreStaysInsideZeroAndOneHundred() {
        assertEquals(0, step(2, 9.0, 0.0, false, false));
        assertEquals(100, Separatism.step(90, 0.0, 5.0, 0.0, 6, 4, 1.0, false, false));
        assertEquals(Separatism.Stage.INDEPENDENCE, Separatism.stage(100));
        assertEquals(Separatism.Stage.CALM, Separatism.stage(-4));
    }

    @Test
    void capitalDoesNotRiseAboveUnrest() {
        assertEquals(79, Separatism.step(70, 0.0, 5.0, 0.0, 6, 4, 1.0, true, false));
        assertEquals(79, Separatism.step(79, 1.0, 5.0, 0.0, 6, 4, 1.0, true, false));
        assertEquals(75, step(79, 6.0, 1.0, true, false));
        assertEquals(79, step(100, 9.0, 0.0, true, false));
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
