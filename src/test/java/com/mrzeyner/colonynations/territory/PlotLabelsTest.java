package com.mrzeyner.colonynations.territory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mrzeyner.colonynations.nation.CityRef;

import org.junit.jupiter.api.Test;

class PlotLabelsTest {
    @Test
    void coreNamesTheNearestCityAndDistantLandStaysAnnexed() {
        CityRef alpha = new CityRef("minecraft:overworld", 1);
        CityRef beta = new CityRef("minecraft:overworld", 2);
        Set<ChunkRef> alphaClaim = Set.of(chunk(0, 0));
        Set<ChunkRef> betaClaim = Set.of(chunk(6, 0));
        ChunkRef nearAlpha = chunk(2, 0);
        ChunkRef nearBeta = chunk(4, 0);
        ChunkRef annexed = chunk(10, 0);

        Map<ChunkRef, PlotLabels.Label> labels = PlotLabels.ofState(
                Set.of(nearAlpha, nearBeta, annexed),
                List.of(alphaClaim, betaClaim),
                List.of(new NearestCity.Anchor(alpha, 0, 0), new NearestCity.Anchor(beta, 6, 0)),
                Map.of(alpha, "Alpha", beta, "Beta"),
                2);

        assertEquals(PlotLabels.Label.core(alpha, "Alpha"), labels.get(nearAlpha));
        assertEquals(PlotLabels.Label.core(beta, "Beta"), labels.get(nearBeta));
        assertEquals(PlotLabels.Label.state(), labels.get(annexed));
    }

    @Test
    void zeroWidthLeavesEveryChunkAnnexed() {
        CityRef alpha = new CityRef("minecraft:overworld", 1);
        ChunkRef beside = chunk(1, 0);

        Map<ChunkRef, PlotLabels.Label> labels = PlotLabels.ofState(
                Set.of(beside),
                List.of(Set.of(chunk(0, 0))),
                List.of(new NearestCity.Anchor(alpha, 0, 0)),
                Map.of(alpha, "Alpha"),
                0);

        assertEquals(PlotLabels.Label.state(), labels.get(beside));
    }

    private static ChunkRef chunk(int x, int z) {
        return new ChunkRef("minecraft:overworld", x, z);
    }
}
