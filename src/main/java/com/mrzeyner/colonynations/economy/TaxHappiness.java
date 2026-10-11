package com.mrzeyner.colonynations.economy;

/**
 * Happiness factor for the tax rate. Neutral rate is 1.0, zero tax is a bonus, the maximum rate is a penalty.
 * Values between those points are linear. MineColonies ignores a factor of exactly 1.0.
 */
public final class TaxHappiness {
    private TaxHappiness() {
    }

    public static double multiplier(int rate, int neutralRate, int maxRate, double bonus, double penalty) {
        int high = Math.max(0, maxRate);
        int mid = Math.max(0, Math.min(neutralRate, high));
        int value = Math.max(0, Math.min(rate, high));
        if (value <= mid) {
            if (mid == 0) {
                return 1.0;
            }
            return lerp(bonus, 1.0, value / (double) mid);
        }
        if (high == mid) {
            return 1.0;
        }
        return lerp(1.0, penalty, (value - mid) / (double) (high - mid));
    }

    private static double lerp(double start, double end, double t) {
        return start + (end - start) * t;
    }
}
