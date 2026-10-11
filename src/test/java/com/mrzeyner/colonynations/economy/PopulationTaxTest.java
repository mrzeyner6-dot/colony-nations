package com.mrzeyner.colonynations.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class PopulationTaxTest {
    @Test
    void adultsPayTheRateAndCitiesWithNoAdultsPayNothing() {
        PopulationTax.Collection collection = PopulationTax.sum(List.of(10, 0, 3), 2);
        assertEquals(26L, collection.income());
        assertFalse(collection.overflow());
        assertEquals(0L, PopulationTax.sum(List.of(4), 0).income());
        assertEquals(0L, PopulationTax.sum(List.of(0, -1), 6).income());
        assertEquals(0L, PopulationTax.sum(List.of(), 2).income());
    }

    @Test
    void sumClampsWhenACityOverflows() {
        PopulationTax.Collection collection = PopulationTax.sum(
                List.of(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE),
                Integer.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, collection.income());
        assertTrue(collection.overflow());
    }

    @Test
    void depositUsesAddExactAndClamps() {
        PopulationTax.Deposit exact = PopulationTax.deposit(100L, 40L);
        assertEquals(140L, exact.treasury());
        assertFalse(exact.overflow());

        PopulationTax.Deposit untouched = PopulationTax.deposit(100L, 0L);
        assertEquals(100L, untouched.treasury());
        assertFalse(untouched.overflow());

        PopulationTax.Deposit overflow = PopulationTax.deposit(Long.MAX_VALUE - 5L, 10L);
        assertEquals(Long.MAX_VALUE, overflow.treasury());
        assertTrue(overflow.overflow());
    }

    @Test
    void rateChangesOncePerDay() {
        assertTrue(PopulationTax.canChange(null, 0L));
        assertFalse(PopulationTax.canChange(4L, 4L));
        assertTrue(PopulationTax.canChange(4L, 5L));
        assertFalse(PopulationTax.canChange(8L, 5L));
    }

    @Test
    void boundsClampToTheConfiguredMaximum() {
        assertEquals(2, PopulationTax.bounded(2, 6));
        assertEquals(6, PopulationTax.bounded(9, 6));
        assertEquals(0, PopulationTax.bounded(-3, 6));
        assertEquals(0, PopulationTax.bounded(4, 0));
    }

    @Test
    void offlineDaysMergeIntoOneAccrual() {
        PopulationTax.Accrual first = PopulationTax.accrue(null, 40L);
        assertEquals(new PopulationTax.Accrual(40L, 1), first);
        PopulationTax.Accrual second = PopulationTax.accrue(first, 15L);
        assertEquals(new PopulationTax.Accrual(55L, 2), second);
        assertEquals(second, PopulationTax.accrue(second, 0L));

        PopulationTax.Accrual overflow = PopulationTax.accrue(new PopulationTax.Accrual(Long.MAX_VALUE, 3), 1L);
        assertEquals(Long.MAX_VALUE, overflow.spurs());
        assertEquals(4, overflow.days());
        assertNull(PopulationTax.parseAccrual("nope", "1"));
        assertEquals(second, PopulationTax.parseAccrual("55", "2"));
    }
}
