package com.mrzeyner.colonynations.territory;

import java.util.Objects;
import java.util.Set;

/**
 * Whether a chunk touches a nation's land (state or city) on one of the four orthogonal sides.
 */
public final class NationReach {
    private NationReach() {
    }

    public static boolean touches(ChunkRef chunk, Set<ChunkRef> nationLand) {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(nationLand, "nationLand");
        return nationLand.contains(new ChunkRef(chunk.dimension(), chunk.x() + 1, chunk.z()))
                || nationLand.contains(new ChunkRef(chunk.dimension(), chunk.x() - 1, chunk.z()))
                || nationLand.contains(new ChunkRef(chunk.dimension(), chunk.x(), chunk.z() + 1))
                || nationLand.contains(new ChunkRef(chunk.dimension(), chunk.x(), chunk.z() - 1));
    }
}
