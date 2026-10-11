package com.mrzeyner.colonynations.territory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import com.mrzeyner.colonynations.nation.CityRef;

import org.junit.jupiter.api.Test;

class SecessionLandTest {
    private static final String OVERWORLD = "minecraft:overworld";

    @Test
    void chunkNearestToTheLeavingCityMoves() {
        CityRef leaving = city(2);
        CityRef staying = city(8);
        ChunkRef nearLeaving = new ChunkRef(OVERWORLD, 1, 0);
        ChunkRef nearStaying = new ChunkRef(OVERWORLD, 20, 0);

        Set<ChunkRef> moving = SecessionLand.attributed(
                List.of(nearLeaving, nearStaying),
                List.of(anchor(leaving, 0, 0), anchor(staying, 20, 0)),
                leaving);

        assertEquals(Set.of(nearLeaving), moving);
    }

    @Test
    void equalDistanceStaysWhenTheLeavingCityLosesTheTie() {
        CityRef leaving = city(8);
        CityRef staying = city(3);
        ChunkRef tied = new ChunkRef(OVERWORLD, 0, 0);

        Set<ChunkRef> moving = SecessionLand.attributed(
                List.of(tied),
                List.of(anchor(leaving, 2, 0), anchor(staying, 0, 2)),
                leaving);

        assertTrue(moving.isEmpty());
    }

    @Test
    void otherDimensionsAreNotAttributed() {
        CityRef leaving = city(1);
        ChunkRef nether = new ChunkRef("minecraft:the_nether", 0, 0);

        Set<ChunkRef> moving = SecessionLand.attributed(
                List.of(nether),
                List.of(anchor(leaving, 0, 0)),
                leaving);

        assertTrue(moving.isEmpty());
    }

    @Test
    void noAnchorForTheLeavingCityMovesNothing() {
        CityRef leaving = city(1);
        CityRef staying = city(2);
        ChunkRef chunk = new ChunkRef(OVERWORLD, 0, 0);

        Set<ChunkRef> moving = SecessionLand.attributed(
                List.of(chunk),
                List.of(anchor(staying, 0, 0)),
                leaving);

        assertTrue(moving.isEmpty());
    }

    private static CityRef city(int colonyId) {
        return new CityRef(OVERWORLD, colonyId);
    }

    private static NearestCity.Anchor anchor(CityRef city, int x, int z) {
        return new NearestCity.Anchor(city, x, z);
    }
}
