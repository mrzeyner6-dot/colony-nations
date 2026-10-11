package com.mrzeyner.colonynations.data;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.mrzeyner.colonynations.nation.CityRef;

/**
 * A colony that left its nation and did not join another one.
 */
public record FreeCity(CityRef city, String name, int color) {
    public static final int GRAY = 0x8D8D8D;

    public FreeCity {
        name = name == null ? "" : name;
    }

    /**
     * Map id for this colony. It is not a nation in the registry; the client still draws it as one polygon group.
     */
    public static UUID mapId(CityRef city) {
        String key = "colony_nations:free:" + city.dimension() + ":" + city.colonyId();
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }
}
