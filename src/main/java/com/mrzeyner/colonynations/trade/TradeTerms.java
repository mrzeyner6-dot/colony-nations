package com.mrzeyner.colonynations.trade;

/**
 * Price a buyer pays. {@code ceil(base * (1 + tariff/100))}.
 * A private buyer pays the base price and ignores embargo.
 */
public final class TradeTerms {
    public record Quote(boolean allowed, long price) {
    }

    private TradeTerms() {
    }

    /**
     * @param maxTariff highest percent the server allows; a larger tariff is clamped
     */
    public static Quote quote(long base, int tariffPercent, boolean embargo, boolean privateBuyer, int maxTariff) {
        // TODO(T-diplomacy) alliance discount applies before this ceiling
        if (base <= 0L) {
            return new Quote(false, 0L);
        }
        if (privateBuyer) {
            return new Quote(true, base);
        }
        if (embargo) {
            return new Quote(false, base);
        }
        int tariff = clamp(tariffPercent, maxTariff);
        return new Quote(true, charge(base, tariff));
    }

    public static int clamp(int tariffPercent, int maxTariff) {
        int max = Math.max(0, maxTariff);
        if (tariffPercent < 0) {
            return 0;
        }
        return Math.min(tariffPercent, max);
    }

    public static long charge(long base, int tariffPercent) {
        if (base <= 0L || tariffPercent <= 0) {
            return Math.max(0L, base);
        }
        long factor = 100L + tariffPercent;
        try {
            long product = Math.multiplyExact(base, factor);
            return (product + 99L) / 100L;
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }
}
