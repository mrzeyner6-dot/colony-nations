package com.mrzeyner.colonynations.territory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.mrzeyner.colonynations.nation.CityRef;

import org.junit.jupiter.api.Test;

class NearestCityTest {
    @Test
    void closerCenterWinsEvenWhenChebyshevDistanceTies() {
        ChunkRef chunk = new ChunkRef("minecraft:overworld", 0, 0);
        CityRef diagonal = city("minecraft:overworld", 1);
        CityRef straight = city("minecraft:overworld", 2);

        CityRef found = NearestCity.find(chunk, List.of(
                new NearestCity.Anchor(straight, 2, 0),
                new NearestCity.Anchor(diagonal, 1, 1))).orElseThrow();

        assertEquals(diagonal, found);
    }

    @Test
    void equalDistanceKeepsSmallerColonyId() {
        ChunkRef chunk = new ChunkRef("minecraft:overworld", 0, 0);
        CityRef higher = city("minecraft:overworld", 8);
        CityRef lower = city("minecraft:overworld", 3);

        CityRef found = NearestCity.find(chunk, List.of(
                new NearestCity.Anchor(higher, 2, 0),
                new NearestCity.Anchor(lower, 0, 2))).orElseThrow();

        assertEquals(lower, found);
    }

    @Test
    void citiesInAnotherDimensionAreIgnored() {
        ChunkRef chunk = new ChunkRef("minecraft:overworld", 0, 0);
        CityRef nether = city("minecraft:the_nether", 1);
        CityRef overworld = city("minecraft:overworld", 9);

        CityRef found = NearestCity.find(chunk, List.of(
                new NearestCity.Anchor(nether, 0, 0),
                new NearestCity.Anchor(overworld, 4, 0))).orElseThrow();

        assertEquals(overworld, found);
        assertTrue(NearestCity.find(chunk, List.of(new NearestCity.Anchor(nether, 0, 0))).isEmpty());
    }

    private static CityRef city(String dimension, int colonyId) {
        return new CityRef(dimension, colonyId);
    }
}
