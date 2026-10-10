package com.mrzeyner.colonynations.territory;

import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

import com.mrzeyner.colonynations.nation.CityRef;

/**
 * Picks the nation city whose colony center is nearest to a chunk.
 * Distance is squared Euclidean distance in chunk coordinates, and only cities in the chunk's dimension count.
 * Equal distance keeps the smaller {@link CityRef}: lower colony id, then dimension string.
 */
public final class NearestCity {
    private static final Comparator<CityRef> TIE = Comparator.comparingInt(CityRef::colonyId).thenComparing(CityRef::dimension);

    /**
     * @param centerChunkX chunk x of the colony center
     * @param centerChunkZ chunk z of the colony center
     */
    public record Anchor(CityRef city, int centerChunkX, int centerChunkZ) {
        public Anchor {
            Objects.requireNonNull(city, "city");
        }
    }

    private NearestCity() {
    }

    public static Optional<CityRef> find(ChunkRef chunk, Collection<Anchor> cities) {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(cities, "cities");
        CityRef best = null;
        long bestDistance = Long.MAX_VALUE;
        for (Anchor anchor : cities) {
            if (!chunk.dimension().equals(anchor.city().dimension())) {
                continue;
            }
            long dx = (long) chunk.x() - anchor.centerChunkX();
            long dz = (long) chunk.z() - anchor.centerChunkZ();
            long distance = dx * dx + dz * dz;
            if (best == null || distance < bestDistance || (distance == bestDistance && TIE.compare(anchor.city(), best) < 0)) {
                best = anchor.city();
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }
}
