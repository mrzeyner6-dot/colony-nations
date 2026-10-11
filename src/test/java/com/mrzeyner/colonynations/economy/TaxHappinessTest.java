package com.mrzeyner.colonynations.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TaxHappinessTest {
    private static final double BONUS = 1.15;
    private static final double PENALTY = 0.70;

    @Test
    void neutralRateIsOneAndTheEndsMatchTheConfigPoints() {
        assertEquals(1.0, TaxHappiness.multiplier(2, 2, 6, BONUS, PENALTY), 1.0e-9);
        assertEquals(BONUS, TaxHappiness.multiplier(0, 2, 6, BONUS, PENALTY), 1.0e-9);
        assertEquals(PENALTY, TaxHappiness.multiplier(6, 2, 6, BONUS, PENALTY), 1.0e-9);
    }

    @Test
    void betweenThePointsIsLinear() {
        assertEquals(1.075, TaxHappiness.multiplier(1, 2, 6, BONUS, PENALTY), 1.0e-9);
        assertEquals(0.85, TaxHappiness.multiplier(4, 2, 6, BONUS, PENALTY), 1.0e-9);
    }

    @Test
    void rateIsClampedToTheConfiguredEnds() {
        assertEquals(BONUS, TaxHappiness.multiplier(-3, 2, 6, BONUS, PENALTY), 1.0e-9);
        assertEquals(PENALTY, TaxHappiness.multiplier(20, 2, 6, BONUS, PENALTY), 1.0e-9);
    }

    @Test
    void collapsedNeutralOrMaxStaysAtOne() {
        assertEquals(1.0, TaxHappiness.multiplier(0, 0, 6, BONUS, PENALTY), 1.0e-9);
        assertEquals(1.0, TaxHappiness.multiplier(4, 4, 4, BONUS, PENALTY), 1.0e-9);
        assertEquals(PENALTY, TaxHappiness.multiplier(6, 0, 6, BONUS, PENALTY), 1.0e-9);
    }
}
