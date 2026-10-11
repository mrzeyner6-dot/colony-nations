package com.mrzeyner.colonynations.economy;

import java.util.List;

/**
 * Folds several offline tax days into one pending chat line.
 */
public final class TaxNotices {
    public static final String ONE_DAY = "command.colony_nations.tax.collected";
    public static final String MANY_DAYS = "command.colony_nations.tax.collected_days";

    public record Line(String key, List<String> args) {
        public Line {
            args = List.copyOf(args);
        }
    }

    private TaxNotices() {
    }

    public static boolean isTax(String key) {
        return ONE_DAY.equals(key) || MANY_DAYS.equals(key);
    }

    /**
     * @param previous tax lines already waiting for this owner; other keys are ignored
     * @param collectedToday spurs gathered on the day being folded in
     * @param treasury treasury after today's credit, shown in the line
     */
    public static Line accrue(List<Line> previous, long collectedToday, long treasury) {
        if (collectedToday < 0) {
            throw new IllegalArgumentException("collected amount must be non-negative");
        }
        long spurs = collectedToday;
        int days = 1;
        if (previous != null) {
            for (Line line : previous) {
                if (line == null || !isTax(line.key())) {
                    continue;
                }
                spurs = Tax.add(spurs, readSpurs(line)).value();
                days = saturatingDays(days, readDays(line));
            }
        }
        if (days <= 1) {
            return new Line(ONE_DAY, List.of(Long.toString(spurs), Long.toString(treasury)));
        }
        return new Line(MANY_DAYS, List.of(Long.toString(spurs), Integer.toString(days), Long.toString(treasury)));
    }

    private static long readSpurs(Line line) {
        if (line.args().isEmpty()) {
            return 0L;
        }
        return parseNonNegative(line.args().get(0));
    }

    private static int readDays(Line line) {
        if (MANY_DAYS.equals(line.key()) && line.args().size() >= 2) {
            long parsed = parseNonNegative(line.args().get(1));
            if (parsed < 1L) {
                return 1;
            }
            return parsed > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) parsed;
        }
        return 1;
    }

    private static long parseNonNegative(String raw) {
        try {
            return Math.max(0L, Long.parseLong(raw));
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private static int saturatingDays(int left, int right) {
        long sum = (long) left + right;
        if (sum > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) sum;
    }
}
