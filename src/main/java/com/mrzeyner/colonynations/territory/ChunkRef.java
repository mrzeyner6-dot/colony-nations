package com.mrzeyner.colonynations.territory;

/**
 * One chunk. The dimension is a resource location string so this record stays free of Minecraft types.
 *
 * @param dimension dimension id, for example {@code minecraft:overworld}
 * @param x chunk x
 * @param z chunk z
 */
public record ChunkRef(String dimension, int x, int z) {
    public ChunkRef {
        if (dimension == null || dimension.isBlank()) {
            throw new IllegalArgumentException("dimension");
        }
    }
}
