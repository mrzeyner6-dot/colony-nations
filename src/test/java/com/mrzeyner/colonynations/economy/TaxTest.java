package com.mrzeyner.colonynations.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class TaxTest {
    @Test
    void incomeIsAdultsTimesRate() {
        assertEquals(0L, Tax.income(0, 2));
        assertEquals(0L, Tax.income(10, 0));
        assertEquals(20L, Tax.income(10, 2));
        assertEquals((long) Integer.MAX_VALUE * 6L, Tax.income(Integer.MAX_VALUE, 6));
    }

    @Test
    void incomeRejectsNegatives() {
        assertThrows(IllegalArgumentException.class, () -> Tax.income(-1, 2));
        assertThrows(IllegalArgumentException.class, () -> Tax.income(1, -1));
    }

    @Test
    void billSumsCities() {
        Tax.Added bill = Tax.bill(List.of(3, 0, 2), 2);
        assertEquals(10L, bill.value());
        assertFalse(bill.overflow());
        assertEquals(new Tax.Added(0L, false), Tax.bill(List.of(), 6));
    }

    @Test
    void treasuryOverflowClampsToMaxLong() {
        Tax.Added fitted = Tax.add(10L, 20L);
        assertEquals(30L, fitted.value());
        assertFalse(fitted.overflow());

        Tax.Added exact = Tax.add(Long.MAX_VALUE, 0L);
        assertEquals(Long.MAX_VALUE, exact.value());
        assertFalse(exact.overflow());

        Tax.Added overflow = Tax.add(Long.MAX_VALUE - 5L, 10L);
        assertEquals(Long.MAX_VALUE, overflow.value());
        assertTrue(overflow.overflow());

        Tax.Added fromNegative = Tax.add(-4L, 10L);
        assertEquals(6L, fromNegative.value());
        assertFalse(fromNegative.overflow());
    }

    @Test
    void addRejectsANegativeAmount() {
        assertThrows(IllegalArgumentException.class, () -> Tax.add(1L, -1L));
    }

    @Test
    void rateBounds() {
        assertTrue(Tax.accepted(0, 6));
        assertTrue(Tax.accepted(6, 6));
        assertFalse(Tax.accepted(7, 6));
        assertFalse(Tax.accepted(-1, 6));
        assertEquals(4, Tax.bounded(6, 4));
        assertEquals(0, Tax.bounded(-3, 6));
        assertEquals(2, Tax.bounded(2, 6));
        assertEquals(0, Tax.bounded(2, -1));
    }

    @Test
    void rateChangesAtMostOncePerDay() {
        assertTrue(Tax.canChange(null, 0L));
        assertFalse(Tax.canChange(4L, 4L));
        assertTrue(Tax.canChange(4L, 5L));
        assertFalse(Tax.canChange(4L, 3L));
    }
}
