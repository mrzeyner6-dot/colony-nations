package com.mrzeyner.colonynations.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TaxHappinessTest {
    private static final double BONUS = 1.15D;
    private static final double PENALTY = 0.70D;

    @Test
    void anchorsAreExact() {
        assertEquals(BONUS, factor(0), 0.0D);
        assertEquals(1.0D, factor(2), 0.0D);
        assertEquals(PENALTY, factor(6), 0.0D);
    }

    @Test
    void betweenAnchorsIsLinear() {
        assertEquals(1.075D, factor(1), 1.0e-9);
        assertEquals(0.925D, factor(3), 1.0e-9);
        assertEquals(0.85D, factor(4), 1.0e-9);
        assertEquals(0.775D, factor(5), 1.0e-9);
    }

    @Test
    void outsideTheRangeClampsToTheEnds() {
        assertEquals(BONUS, TaxHappiness.factor(-3, 2, 6, BONUS, PENALTY), 0.0D);
        assertEquals(PENALTY, TaxHappiness.factor(9, 2, 6, BONUS, PENALTY), 0.0D);
    }

    @Test
    void collapsedNeutralAndMaxStayAtOne() {
        assertEquals(1.0D, TaxHappiness.factor(0, 0, 0, BONUS, PENALTY), 0.0D);
        assertEquals(1.0D, TaxHappiness.factor(4, 6, 4, BONUS, PENALTY), 0.0D);
    }

    private static double factor(int rate) {
        return TaxHappiness.factor(rate, 2, 6, BONUS, PENALTY);
    }
}
