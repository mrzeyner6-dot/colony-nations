package com.mrzeyner.colonynations.territory;

/**
 * Chunk distance used when charging for annexation.
 * The nearest city is chosen by squared Euclidean distance; the price uses that same distance, rounded to a long.
 */
public final class ChunkDistance {
    private ChunkDistance() {
    }

    public static long rounded(int fromX, int fromZ, int toX, int toZ) {
        long dx = (long) fromX - toX;
        long dz = (long) fromZ - toZ;
        return Math.round(Math.sqrt(dx * dx + dz * dz));
    }
}
