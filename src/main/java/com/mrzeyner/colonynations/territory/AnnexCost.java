package com.mrzeyner.colonynations.territory;

/**
 * Price of annexing one chunk: base plus a per-chunk charge times the distance to the nearest city.
 */
public final class AnnexCost {
    private AnnexCost() {
    }

    public static long price(int chunkDistance, int base, int perChunk) {
        if (chunkDistance < 0) {
            throw new IllegalArgumentException("chunkDistance");
        }
        return (long) base + (long) perChunk * chunkDistance;
    }
}
