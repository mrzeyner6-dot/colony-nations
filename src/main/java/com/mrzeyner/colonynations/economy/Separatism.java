package com.mrzeyner.colonynations.economy;

/**
 * Per-city separatism, from 0 to 100.
 * The score grows only while the state presses (tax above the neutral rate) or the city has collapsed.
 * A grace period blocks growth. Otherwise the score falls.
 * A capital never rises above {@link #CAPITAL_CAP} on the daily step.
 */
public final class Separatism {
    public static final int MIN = 0;
    public static final int MAX = 100;
    public static final int CAPITAL_CAP = 79;
    public static final int UNREST_AT = 50;
    public static final int MOVEMENT_AT = 80;

    /** Map stroke: calm or independence. */
    public static final int BAND_CALM = 0;
    /** Map stroke: unrest, scores 50–79. */
    public static final int BAND_UNREST = 1;
    /** Map stroke: secession movement, scores 80–99. */
    public static final int BAND_MOVEMENT = 2;

    public enum Stage {
        CALM,
        UNREST,
        MOVEMENT,
        INDEPENDENCE
    }

    /** Why the scale is moving, shown beside it. Order is the menu payload. */
    public enum Cause {
        CALM,
        GRACE,
        TAX,
        COLLAPSE
    }

    /**
     * Upward stage changes. Decaying into a lower stage does not count.
     * Landing on 100 is independence and does not also count as unrest or a secession movement.
     */
    public record Crossing(boolean unrest, boolean movement, boolean independence) {
    }

    private Separatism() {
    }

    public static int clamp(int score) {
        return Math.max(MIN, Math.min(MAX, score));
    }

    public static Stage stage(int score) {
        int value = clamp(score);
        if (value >= MAX) {
            return Stage.INDEPENDENCE;
        }
        if (value >= MOVEMENT_AT) {
            return Stage.MOVEMENT;
        }
        if (value >= UNREST_AT) {
            return Stage.UNREST;
        }
        return Stage.CALM;
    }

    public static boolean unrest(int score) {
        return mapBand(score) == BAND_UNREST;
    }

    /**
     * Which map outline a city uses. Only a change of this value should redraw the polygon.
     */
    public static int mapBand(int score) {
        return switch (stage(score)) {
            case UNREST -> BAND_UNREST;
            case MOVEMENT -> BAND_MOVEMENT;
            default -> BAND_CALM;
        };
    }

    /**
     * Tax pressure, from 0 to 1. Zero at or below the neutral rate.
     * Mobilisation and occupation can be added here later.
     */
    public static double burden(int taxRate, int neutralRate, int maxRate) {
        if (taxRate <= neutralRate) {
            return 0.0d;
        }
        int span = maxRate - neutralRate;
        if (span <= 0) {
            return 1.0d;
        }
        double value = (taxRate - (double) neutralRate) / span;
        if (value < 0.0d) {
            return 0.0d;
        }
        return Math.min(1.0d, value);
    }

    /**
     * Days of grace still left, including today. Zero when the period has ended or was never started.
     */
    public static int daysLeft(Long startDay, long today, int graceDays) {
        if (startDay == null || graceDays <= 0) {
            return 0;
        }
        long end;
        try {
            end = Math.addExact(startDay, graceDays);
        } catch (ArithmeticException overflow) {
            return Integer.MAX_VALUE;
        }
        if (today >= end) {
            return 0;
        }
        long left = end - today;
        return left > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) left;
    }

    /**
     * Grace wins over collapse, and collapse wins over tax, so the line explains why the score is held or rising.
     */
    public static Cause cause(
            double happiness,
            double unrestThreshold,
            double collapseThreshold,
            int taxRate,
            int neutralRate,
            int daysLeft) {
        if (daysLeft > 0) {
            return Cause.GRACE;
        }
        if (happiness < collapseThreshold) {
            return Cause.COLLAPSE;
        }
        if (taxRate > neutralRate && happiness < unrestThreshold) {
            return Cause.TAX;
        }
        return Cause.CALM;
    }

    /**
     * Fractional growth is rounded to the nearest score point. Half rounds away from zero via {@link Math#round}.
     * Collapse uses a fixed burden of 0.5 and ignores the tax burden. Grace blocks growth and still allows decay.
     */
    public static int step(
            int current,
            double happiness,
            double unrestThreshold,
            double collapseThreshold,
            int growth,
            int decay,
            double burden,
            boolean capital,
            boolean grace) {
        int start = clamp(current);
        boolean collapse = happiness < collapseThreshold;
        double factor = collapse ? 0.5d : Math.max(0.0d, Math.min(1.0d, burden));
        boolean pressing = !collapse && factor > 0.0d && happiness < unrestThreshold;
        boolean rising = (collapse || pressing) && unrestThreshold > happiness && factor > 0.0d;
        int next;
        if (rising && grace) {
            next = start;
        } else if (rising) {
            double raw = (unrestThreshold - happiness) * Math.max(0, growth) * factor;
            next = clamp(start + (int) Math.round(raw));
        } else {
            next = clamp(start - Math.max(0, decay));
        }
        if (capital) {
            next = Math.min(next, CAPITAL_CAP);
        }
        return next;
    }

    public static Crossing crossing(int before, int after) {
        int from = clamp(before);
        int to = clamp(after);
        if (to < from) {
            return new Crossing(false, false, false);
        }
        return new Crossing(
                from < UNREST_AT && to >= UNREST_AT && to < MOVEMENT_AT,
                from < MOVEMENT_AT && to >= MOVEMENT_AT && to < MAX,
                from < MAX && to >= MAX);
    }
}
