package com.mrzeyner.colonynations.territory;

/**
 * How many state-land chunks a nation may hold.
 */
public final class AdminLimit {
    private AdminLimit() {
    }

    public static long chunks(int base, int perCity, int cityCount, int perTownHallLevel, int townHallLevels) {
        return (long) base + (long) perCity * cityCount + (long) perTownHallLevel * townHallLevels;
    }
}
