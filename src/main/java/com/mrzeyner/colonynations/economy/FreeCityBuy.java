package com.mrzeyner.colonynations.economy;

/**
 * Price, range and cooldown for buying a free city.
 * Distance is Chebyshev chunks; {@link Integer#MAX_VALUE} means there is no land in that dimension.
 */
public final class FreeCityBuy {
    public static final int OK = 0;
    public static final int TOO_FAR = 1;
    public static final int COOLDOWN = 2;
    public static final int POOR = 3;

    public record Quote(long price, int distance, int block, long readyDay) {
    }

    private FreeCityBuy() {
    }

    /**
     * @param multiplier applied only when {@code returning} is true; half-spurs round to the nearest spur
     */
    public static long price(long base, long perCitizen, int adults, boolean returning, double multiplier) {
        long fixed = Math.max(0L, base);
        long each = Math.max(0L, perCitizen);
        int people = Math.max(0, adults);
        long cost;
        try {
            cost = Math.addExact(fixed, Math.multiplyExact(each, people));
        } catch (ArithmeticException overflow) {
            cost = Long.MAX_VALUE;
        }
        if (!returning || multiplier == 1.0d) {
            return cost;
        }
        if (cost == Long.MAX_VALUE || multiplier <= 0.0d) {
            return cost == Long.MAX_VALUE ? Long.MAX_VALUE : 0L;
        }
        double scaled = cost * multiplier;
        if (scaled >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, Math.round(scaled));
    }

    public static boolean inRange(int distance, int radius) {
        return radius >= 0 && distance >= 0 && distance <= radius;
    }

    /**
     * A city may be bought again on {@code lastJoinDay + cooldownDays}. No previous join is always allowed.
     */
    public static boolean cooled(Long lastJoinDay, long today, int cooldownDays) {
        if (cooldownDays <= 0 || lastJoinDay == null) {
            return true;
        }
        try {
            return today >= Math.addExact(lastJoinDay, cooldownDays);
        } catch (ArithmeticException overflow) {
            return false;
        }
    }

    public static long readyDay(Long lastJoinDay, long today, int cooldownDays) {
        if (lastJoinDay == null || cooldownDays <= 0) {
            return today;
        }
        try {
            return Math.addExact(lastJoinDay, cooldownDays);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    /**
     * Far, then cooldown, then treasury. The price is still filled in when the buy is blocked.
     */
    public static Quote quote(
            long base,
            long perCitizen,
            int adults,
            boolean returning,
            double multiplier,
            int distance,
            int radius,
            long treasury,
            Long lastJoinDay,
            long today,
            int cooldownDays) {
        long cost = price(base, perCitizen, adults, returning, multiplier);
        long ready = readyDay(lastJoinDay, today, cooldownDays);
        int block = OK;
        if (!inRange(distance, radius)) {
            block = TOO_FAR;
        } else if (!cooled(lastJoinDay, today, cooldownDays)) {
            block = COOLDOWN;
        } else if (treasury < cost) {
            block = POOR;
        }
        return new Quote(cost, distance, block, ready);
    }
}
