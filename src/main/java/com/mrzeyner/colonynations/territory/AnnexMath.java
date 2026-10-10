package com.mrzeyner.colonynations.territory;

/**
 * Annex price and the administrative cap on state-land chunks.
 */
public final class AnnexMath {
    private AnnexMath() {
    }

    public static long price(int baseCost, int costPerChunkDistance, long distanceChunks) {
        return (long) baseCost + (long) costPerChunkDistance * distanceChunks;
    }

    public static int limit(int base, int perCity, int cities, int perTownHallLevel, int townHallLevelSum) {
        return base + perCity * cities + perTownHallLevel * townHallLevelSum;
    }
}
