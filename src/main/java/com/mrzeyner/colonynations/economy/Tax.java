package com.mrzeyner.colonynations.economy;

import java.util.List;

/**
 * Daily population tax. Children are excluded by the caller; this class only multiplies adult counts by the rate.
 */
public final class Tax {
    /**
     * @param value result after a saturating add
     * @param overflow true when the sum did not fit in a long and {@code value} is {@link Long#MAX_VALUE}
     */
    public record Added(long value, boolean overflow) {
    }

    private Tax() {
    }

    public static boolean accepted(int rate, int maxRate) {
        return rate >= 0 && maxRate >= 0 && rate <= maxRate;
    }

    /**
     * Rate actually charged. A stored rate above the configured cap is lowered; a negative rate counts as zero.
     */
    public static int bounded(int stored, int maxRate) {
        int cap = Math.max(0, maxRate);
        if (stored < 0) {
            return 0;
        }
        return Math.min(stored, cap);
    }

    public static long income(int adults, int ratePerAdult) {
        if (adults < 0 || ratePerAdult < 0) {
            throw new IllegalArgumentException("adults and rate must be non-negative");
        }
        return Math.multiplyExact((long) adults, ratePerAdult);
    }

    /**
     * Sums adult counts times the rate. An overflowing total is clamped to {@link Long#MAX_VALUE}.
     */
    public static Added bill(List<Integer> adultsPerCity, int ratePerAdult) {
        long total = 0L;
        boolean overflow = false;
        for (int adults : adultsPerCity) {
            Added next = add(total, income(adults, ratePerAdult));
            total = next.value();
            overflow = overflow || next.overflow();
        }
        return new Added(total, overflow);
    }

    /**
     * Adds a non-negative amount. Positive overflow becomes {@link Long#MAX_VALUE}.
     */
    public static Added add(long left, long right) {
        if (right < 0) {
            throw new IllegalArgumentException("added amount must be non-negative");
        }
        try {
            return new Added(Math.addExact(left, right), false);
        } catch (ArithmeticException overflow) {
            return new Added(Long.MAX_VALUE, true);
        }
    }

    /**
     * A rate may change at most once per game day. A nation that has never changed it may do so immediately.
     */
    public static boolean canChange(Long lastChangeDay, long today) {
        return lastChangeDay == null || today > lastChangeDay;
    }
}
