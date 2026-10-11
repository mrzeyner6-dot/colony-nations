package com.mrzeyner.colonynations.territory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.mrzeyner.colonynations.nation.CityRef;

class NearestCityTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";

    @Test
    void closerCityWins() {
        CityCenter far = center(OVERWORLD, 1, 0, 0);
        CityCenter near = center(OVERWORLD, 9, 4, 1);
        ChunkRef chunk = new ChunkRef(OVERWORLD, 5, 2);

        assertEquals(near.city(), NearestCity.find(chunk, List.of(far, near)).orElseThrow());
        assertEquals(1, NearestCity.chunkDistance(chunk, near.center()));
        assertEquals(5, NearestCity.chunkDistance(chunk, far.center()));
    }

    @Test
    void equalDistancePicksSmallerColonyId() {
        CityCenter higherId = center(OVERWORLD, 8, 10, 0);
        CityCenter lowerId = center(OVERWORLD, 2, -10, 0);
        ChunkRef chunk = new ChunkRef(OVERWORLD, 0, 0);

        assertEquals(10, NearestCity.chunkDistance(chunk, higherId.center()));
        assertEquals(10, NearestCity.chunkDistance(chunk, lowerId.center()));
        assertEquals(lowerId.city(), NearestCity.find(chunk, List.of(higherId, lowerId)).orElseThrow());
    }

    @Test
    void otherDimensionIsIgnoredEvenWhenCloser() {
        CityCenter nether = center(NETHER, 1, 0, 0);
        CityCenter overworld = center(OVERWORLD, 4, 20, 0);
        ChunkRef chunk = new ChunkRef(OVERWORLD, 0, 0);

        assertEquals(overworld.city(), NearestCity.find(chunk, List.of(nether, overworld)).orElseThrow());
        assertTrue(NearestCity.find(chunk, List.of(nether)).isEmpty());
        assertTrue(NearestCity.find(chunk, List.of()).isEmpty());
    }

    @Test
    void diagonalUsesChessboardDistance() {
        ChunkRef origin = new ChunkRef(OVERWORLD, 0, 0);
        ChunkRef diagonal = new ChunkRef(OVERWORLD, 3, -2);
        assertEquals(3, NearestCity.chunkDistance(origin, diagonal));
        assertThrows(IllegalArgumentException.class, () -> NearestCity.chunkDistance(origin, new ChunkRef(NETHER, 0, 0)));
    }

    @Test
    void centerDimensionMustMatchTheCity() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new CityCenter(new CityRef(OVERWORLD, 1), new ChunkRef(NETHER, 0, 0)));
    }

    private static CityCenter center(String dimension, int colonyId, int x, int z) {
        return new CityCenter(new CityRef(dimension, colonyId), new ChunkRef(dimension, x, z));
    }
}
