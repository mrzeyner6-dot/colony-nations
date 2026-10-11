package com.mrzeyner.colonynations.economy;

/**
 * Per-city separatism, from 0 to 100.
 * Below the unrest threshold the score grows; at or above it the score falls.
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
     * Fractional growth is rounded to the nearest score point. Half rounds away from zero via {@link Math#round}.
     */
    public static int advance(int current, double happiness, double threshold, int growth, int decay, boolean capital) {
        int start = clamp(current);
        double raw = happiness < threshold
                ? (threshold - happiness) * Math.max(0, growth)
                : -Math.max(0, decay);
        int next = clamp(start + (int) Math.round(raw));
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
