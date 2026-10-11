package com.mrzeyner.colonynations.territory;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Free state land granted around a city's claim when the city joins a nation.
 * The ring is the chessboard neighbourhood of the claim, excluding the claim itself.
 */
public final class FoundingRing {
    private FoundingRing() {
    }

    public static Set<ChunkRef> around(Collection<ChunkRef> claim, int width) {
        Objects.requireNonNull(claim, "claim");
        if (width <= 0 || claim.isEmpty()) {
            return Set.of();
        }
        Set<ChunkRef> claimed = Set.copyOf(claim);
        Set<ChunkRef> ring = new LinkedHashSet<>();
        for (ChunkRef chunk : claimed) {
            for (int dx = -width; dx <= width; dx++) {
                for (int dz = -width; dz <= width; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    ChunkRef neighbour = new ChunkRef(chunk.dimension(), chunk.x() + dx, chunk.z() + dz);
                    if (!claimed.contains(neighbour)) {
                        ring.add(neighbour);
                    }
                }
            }
        }
        return Set.copyOf(ring);
    }

    /**
     * Drops state land inside the claim (it is city land now) and claims the free ring for the nation.
     * Chunks that already have a state owner, and chunks in {@code cityLand}, stay as they are.
     */
    public static void grant(
            TerritoryMap territory,
            UUID nationId,
            Set<ChunkRef> claim,
            Set<ChunkRef> cityLand,
            int width) {
        Objects.requireNonNull(territory, "territory");
        Objects.requireNonNull(nationId, "nationId");
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(cityLand, "cityLand");
        for (ChunkRef chunk : claim) {
            if (territory.owner(chunk).isPresent()) {
                territory.release(chunk);
            }
        }
        for (ChunkRef chunk : around(claim, width)) {
            if (cityLand.contains(chunk) || territory.owner(chunk).isPresent()) {
                continue;
            }
            territory.claim(chunk, nationId);
        }
    }
}
