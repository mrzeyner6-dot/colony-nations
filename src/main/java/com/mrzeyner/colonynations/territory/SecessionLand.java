package com.mrzeyner.colonynations.territory;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import com.mrzeyner.colonynations.nation.CityRef;

/**
 * State chunks that follow a city out of its nation.
 * A chunk moves when {@link NearestCity} picks the leaving city among the nation's cities.
 * Chunks in a dimension with no city anchor stay where they are.
 */
public final class SecessionLand {
    private SecessionLand() {
    }

    public static Set<ChunkRef> attributed(
            Collection<ChunkRef> state,
            Collection<NearestCity.Anchor> cities,
            CityRef leaving) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(cities, "cities");
        Objects.requireNonNull(leaving, "leaving");
        Set<ChunkRef> moving = new HashSet<>();
        for (ChunkRef chunk : state) {
            if (NearestCity.find(chunk, cities).filter(leaving::equals).isPresent()) {
                moving.add(chunk);
            }
        }
        return Set.copyOf(moving);
    }
}
