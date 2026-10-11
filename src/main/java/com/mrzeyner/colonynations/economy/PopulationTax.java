package com.mrzeyner.colonynations.economy;

import java.util.List;

/**
 * Daily tax from adult citizens. Children are omitted by the caller, which passes adult counts only.
 * Occupied cities are not modeled here.
 */
public final class PopulationTax {
    public record Collection(long income, boolean overflow) {
    }

    public record Deposit(long treasury, boolean overflow) {
    }

    /**
     * Offline tax lines collapse into one total instead of one notice per day.
     */
    public record Accrual(long spurs, int days) {
    }

    private PopulationTax() {
    }

    /**
     * @param max highest allowed rate; negative is treated as 0
     */
    public static int bounded(int rate, int max) {
        int ceiling = Math.max(0, max);
        if (rate < 0) {
            return 0;
        }
        return Math.min(rate, ceiling);
    }

    /**
     * A rate may change once per game day. A nation that has never changed it may change it immediately.
     * {@code today} is the same day number as upkeep ({@code dayTime / 24000}).
     */
    public static boolean canChange(Long lastChangeDay, long today) {
        return lastChangeDay == null || today > lastChangeDay;
    }

    public static Collection sum(List<Integer> adultCounts, int rate) {
        if (adultCounts == null || adultCounts.isEmpty() || rate <= 0) {
            return new Collection(0L, false);
        }
        long total = 0L;
        for (int adults : adultCounts) {
            if (adults <= 0) {
                continue;
            }
            try {
                long city = Math.multiplyExact(adults, (long) rate);
                total = Math.addExact(total, city);
            } catch (ArithmeticException overflow) {
                return new Collection(Long.MAX_VALUE, true);
            }
        }
        return new Collection(total, false);
    }

    /**
     * Adds tax to the treasury. Overflow clamps to {@link Long#MAX_VALUE}; the caller logs a warning.
     */
    public static Deposit deposit(long treasury, long income) {
        if (income <= 0L) {
            return new Deposit(treasury, false);
        }
        try {
            return new Deposit(Math.addExact(treasury, income), false);
        } catch (ArithmeticException overflow) {
            return new Deposit(Long.MAX_VALUE, true);
        }
    }

    public static Accrual accrue(Accrual previous, long dayIncome) {
        if (dayIncome <= 0L) {
            return previous;
        }
        if (previous == null) {
            return new Accrual(dayIncome, 1);
        }
        long spurs;
        try {
            spurs = Math.addExact(previous.spurs, dayIncome);
        } catch (ArithmeticException overflow) {
            spurs = Long.MAX_VALUE;
        }
        int days = previous.days == Integer.MAX_VALUE ? Integer.MAX_VALUE : previous.days + 1;
        return new Accrual(spurs, days);
    }

    public static Accrual parseAccrual(String spurs, String days) {
        try {
            return new Accrual(Long.parseLong(spurs), Integer.parseInt(days));
        } catch (NumberFormatException invalid) {
            return null;
        }
    }
}
