package com.mrzeyner.colonynations.territory;

import java.util.Objects;

import com.mrzeyner.colonynations.nation.CityRef;

/**
 * A nation's city and the chunk that contains the colony center.
 */
public record CityCenter(CityRef city, ChunkRef center) {
    public CityCenter {
        Objects.requireNonNull(city, "city");
        Objects.requireNonNull(center, "center");
        if (!city.dimension().equals(center.dimension())) {
            throw new IllegalArgumentException("city dimension and center dimension differ");
        }
    }
}
