package com.mrzeyner.colonynations.nation;

/**
 * A MineColonies colony inside one dimension. Colony ids are unique per dimension, not globally.
 *
 * @param dimension dimension id as a resource location string, for example {@code minecraft:overworld}
 * @param colonyId colony id inside that dimension
 */
public record CityRef(String dimension, int colonyId) {
    public CityRef {
        if (dimension == null || dimension.isBlank()) {
            throw new IllegalArgumentException("dimension");
        }
    }
}
