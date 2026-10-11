package com.mrzeyner.colonynations.nation;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * A city that left its nation and was not taken in by another.
 * {@link #MAP_ID} paints every free city gray on the map.
 * It is not written into MineColonies: {@code setOwner} needs an online player,
 * and {@code setOwnerAbandoned} assigns a random owner id.
 */
public record FreeCity(CityRef city, String name, int color) {
    /** Gray fill shared by every free city. */
    public static final int GRAY = 0xA0A0A0;

    /**
     * Deterministic id of the string {@code colony_nations:free_city}.
     */
    public static final UUID MAP_ID = UUID.nameUUIDFromBytes(
            "colony_nations:free_city".getBytes(StandardCharsets.UTF_8));

    public FreeCity {
        Objects.requireNonNull(city, "city");
        name = name == null ? "" : name;
    }
}
