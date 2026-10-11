package com.mrzeyner.colonynations.economy;

/**
 * Happiness factor for the tax modifier.
 * Rate 0 maps to the bonus, the neutral rate maps to 1, and the max rate maps to the penalty.
 * The two spans are linear. The neutral point and the max point are exact, not a lerp remainder.
 */
public final class TaxHappiness {
    private TaxHappiness() {
    }

    public static double factor(int rate, int neutralRate, int maxRate, double bonus, double penalty) {
        int max = Math.max(0, maxRate);
        int neutral = Math.min(Math.max(0, neutralRate), max);
        int clamped = Math.min(Math.max(0, rate), max);
        if (clamped == neutral || neutral == 0 && clamped == 0) {
            return 1.0D;
        }
        if (clamped < neutral) {
            double t = clamped / (double) neutral;
            return bonus + (1.0D - bonus) * t;
        }
        int span = max - neutral;
        if (span == 0 || clamped == max) {
            return span == 0 ? 1.0D : penalty;
        }
        double t = (clamped - neutral) / (double) span;
        return 1.0D + (penalty - 1.0D) * t;
    }
}
