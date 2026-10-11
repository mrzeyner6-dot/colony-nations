package com.mrzeyner.colonynations.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SeparatismTest {
    @Test
    void lowHappinessGrowsAndHighHappinessDecays() {
        Separatism.Day grown = advance(0.0D, 4.0D, false);
        assertEquals(6.0D, grown.value(), 0.0D);
        assertEquals(Separatism.Stage.CALM, grown.stage());

        Separatism.Day decayed = advance(10.0D, 10.0D, false);
        assertEquals(6.0D, decayed.value(), 0.0D);
        assertFalse(decayed.notifyUnrest());
    }

    @Test
    void thresholdIsStrict() {
        assertEquals(0.0D, advance(0.0D, 5.0D, false).value(), 0.0D);
        assertEquals(6.0D, advance(0.0D, 4.0D, false).value(), 0.0D);
    }

    @Test
    void boundsClampAtZeroAndOneHundred() {
        assertEquals(0.0D, advance(3.0D, 10.0D, false).value(), 0.0D);
        Separatism.Day full = advance(97.0D, 0.0D, false);
        assertEquals(100.0D, full.value(), 0.0D);
        assertEquals(Separatism.Stage.SECESSION, full.stage());
        assertTrue(full.secede());
        assertFalse(full.notifyMovement());
    }

    @Test
    void stagesAndRisingTransitions() {
        assertEquals(Separatism.Stage.CALM, Separatism.stage(49.9D));
        assertEquals(Separatism.Stage.UNREST, Separatism.stage(50.0D));
        assertEquals(Separatism.Stage.UNREST, Separatism.stage(79.0D));
        assertEquals(Separatism.Stage.MOVEMENT, Separatism.stage(80.0D));
        assertEquals(Separatism.Stage.MOVEMENT, Separatism.stage(99.9D));
        assertEquals(Separatism.Stage.SECESSION, Separatism.stage(100.0D));

        Separatism.Day unrest = advance(40.0D, 0.0D, false);
        assertEquals(70.0D, unrest.value(), 0.0D);
        assertTrue(unrest.notifyUnrest());
        assertFalse(unrest.notifyMovement());

        Separatism.Day movement = advance(70.0D, 0.0D, false);
        assertEquals(100.0D, movement.value(), 0.0D);
        assertTrue(movement.notifyMovement());
        assertTrue(movement.secede());
        assertFalse(movement.notifyUnrest());
    }

    @Test
    void capitalDoesNotGrowAboveSeventyNine() {
        Separatism.Day capped = advance(70.0D, 0.0D, true);
        assertEquals(79.0D, capped.value(), 0.0D);
        assertEquals(Separatism.Stage.UNREST, capped.stage());
        assertFalse(capped.secede());
        assertFalse(capped.notifyMovement());

        Separatism.Day held = advance(79.0D, 0.0D, true);
        assertEquals(79.0D, held.value(), 0.0D);
        assertFalse(held.notifyUnrest());

        Separatism.Day alreadyHigh = advance(90.0D, 0.0D, true);
        assertEquals(90.0D, alreadyHigh.value(), 0.0D);

        Separatism.Day decayed = advance(90.0D, 10.0D, true);
        assertEquals(86.0D, decayed.value(), 0.0D);
    }

    private static Separatism.Day advance(double current, double happiness, boolean capital) {
        return Separatism.advance(current, happiness, capital, 5.0D, 6.0D, 4.0D);
    }
}
