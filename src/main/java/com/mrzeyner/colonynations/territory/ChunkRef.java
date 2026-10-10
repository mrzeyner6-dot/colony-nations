package com.mrzeyner.colonynations.territory;

/**
 * One chunk. Coordinates are chunk coordinates, not block coordinates.
 *
 * @param dimension dimension id as a resource location string, for example {@code minecraft:overworld}
 */
public record ChunkRef(String dimension, int x, int z) {
    public ChunkRef {
        if (dimension == null || dimension.isBlank()) {
            throw new IllegalArgumentException("dimension");
        }
    }

    public ChunkRef offset(int dx, int dz) {
        return new ChunkRef(dimension, x + dx, z + dz);
    }
}
