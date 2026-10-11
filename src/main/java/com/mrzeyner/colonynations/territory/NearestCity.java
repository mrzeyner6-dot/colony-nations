package com.mrzeyner.colonynations.territory;

import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

import com.mrzeyner.colonynations.nation.CityRef;

/**
 * Picks the nearest city of a nation for a chunk.
 * Distance is the chessboard chunk distance ({@code max(|dx|, |dz|)}) inside one dimension.
 * Cities in another dimension are ignored. Equal distances use the smaller colony id, then the dimension string.
 */
public final class NearestCity {
    private static final Comparator<CityRef> TIE_ORDER =
            Comparator.comparingInt(CityRef::colonyId).thenComparing(CityRef::dimension);

    private NearestCity() {
    }

    public static Optional<CityRef> find(ChunkRef chunk, Collection<CityCenter> cities) {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(cities, "cities");
        CityRef best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (CityCenter city : cities) {
            if (!chunk.dimension().equals(city.city().dimension())) {
                continue;
            }
            int distance = chunkDistance(chunk, city.center());
            if (best == null
                    || distance < bestDistance
                    || (distance == bestDistance && TIE_ORDER.compare(city.city(), best) < 0)) {
                best = city.city();
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * Chessboard distance in chunks. Both chunks must share a dimension.
     */
    public static int chunkDistance(ChunkRef from, ChunkRef to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (!from.dimension().equals(to.dimension())) {
            throw new IllegalArgumentException("dimensions differ");
        }
        int dx = from.x() - to.x();
        int dz = from.z() - to.z();
        return Math.max(Math.abs(dx), Math.abs(dz));
    }
}
