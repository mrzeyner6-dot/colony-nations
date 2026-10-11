package com.mrzeyner.colonynations.territory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import com.mrzeyner.colonynations.nation.CityRef;

import org.junit.jupiter.api.Test;

class SecessionLandTest {
    @Test
    void distanceIsChebyshevInsideOneDimension() {
        ChunkRef claim = new ChunkRef("minecraft:overworld", 0, 0);
        assertEquals(3, SecessionLand.chebyshev(claim, new ChunkRef("minecraft:overworld", 2, -3)));
        assertEquals(Integer.MAX_VALUE, SecessionLand.chebyshev(claim, new ChunkRef("minecraft:the_nether", 0, 0)));
        assertEquals(2, SecessionLand.nearest(
                Set.of(claim, new ChunkRef("minecraft:overworld", 10, 10)),
                Set.of(new ChunkRef("minecraft:overworld", 4, 1), new ChunkRef("minecraft:overworld", 1, 2))));
    }

    @Test
    void onlyLandNearestToTheLeavingCityFollows() {
        CityRef leaving = new CityRef("minecraft:overworld", 1);
        CityRef staying = new CityRef("minecraft:overworld", 2);
        ChunkRef nearLeaving = new ChunkRef("minecraft:overworld", 0, 0);
        ChunkRef nearStaying = new ChunkRef("minecraft:overworld", 20, 0);
        Set<ChunkRef> moved = SecessionLand.following(
                leaving,
                List.of(nearLeaving, nearStaying),
                List.of(new NearestCity.Anchor(leaving, 0, 0), new NearestCity.Anchor(staying, 20, 0)));
        assertEquals(Set.of(nearLeaving), moved);
        assertTrue(SecessionLand.following(leaving, List.of(), List.of()).isEmpty());
    }
}
