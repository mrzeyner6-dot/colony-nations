package com.mrzeyner.colonynations.trade;

/**
 * Packs sold and spurs paid into the treasury for the current game day and the day before it.
 * Spurs here are coins taken from the receiver, not credit spent on a later visit.
 */
public record TerminalLedger(long day, int packsToday, long spursToday, int packsYesterday, long spursYesterday) {
    public static TerminalLedger unset() {
        return new TerminalLedger(Long.MIN_VALUE, 0, 0, 0, 0);
    }

    /**
     * Moves the current day into yesterday when exactly one game day has passed.
     * A longer jump had no ticks in between, so yesterday is empty rather than a stale total.
     */
    public TerminalLedger onDay(long today) {
        if (day == Long.MIN_VALUE) {
            return new TerminalLedger(today, 0, 0, 0, 0);
        }
        if (today <= day) {
            return this;
        }
        if (today == day + 1) {
            return new TerminalLedger(today, 0, 0, packsToday, spursToday);
        }
        return new TerminalLedger(today, 0, 0, 0, 0);
    }

    public TerminalLedger record(int packs, long spurs) {
        if (packs <= 0 && spurs <= 0L) {
            return this;
        }
        int nextPacks = packsToday;
        if (packs > 0) {
            nextPacks = packsToday > Integer.MAX_VALUE - packs ? Integer.MAX_VALUE : packsToday + packs;
        }
        long nextSpurs = spursToday;
        if (spurs > 0L) {
            try {
                nextSpurs = Math.addExact(spursToday, spurs);
            } catch (ArithmeticException overflow) {
                nextSpurs = Long.MAX_VALUE;
            }
        }
        return new TerminalLedger(day == Long.MIN_VALUE ? 0L : day, nextPacks, nextSpurs, packsYesterday, spursYesterday);
    }
}
