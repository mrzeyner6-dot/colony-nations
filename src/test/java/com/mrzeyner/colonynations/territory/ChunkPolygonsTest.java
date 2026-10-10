package com.mrzeyner.colonynations.territory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ChunkPolygonsTest {
    @Test
    void neighborsMergeAndAGapBecomesAHole() {
        Set<ChunkRef> chunks = new HashSet<>();
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                if (x != 1 || z != 1) {
                    chunks.add(new ChunkRef("minecraft:overworld", x, z));
                }
            }
        }

        List<ChunkPolygons.Polygon> polygons = ChunkPolygons.of(chunks);

        assertEquals(1, polygons.size());
        ChunkPolygons.Polygon polygon = polygons.get(0);
        assertEquals(1, polygon.holes().size());
        assertTrue(corners(polygon.outer()).contains(new ChunkPolygons.Point(0, 0)));
        assertTrue(corners(polygon.outer()).contains(new ChunkPolygons.Point(3, 3)));
        assertEquals(
                Set.of(
                        new ChunkPolygons.Point(1, 1),
                        new ChunkPolygons.Point(2, 1),
                        new ChunkPolygons.Point(2, 2),
                        new ChunkPolygons.Point(1, 2)),
                corners(polygon.holes().get(0)));
    }

    @Test
    void orthogonalChunksMergeAndDiagonalChunksDoNot() {
        List<ChunkPolygons.Polygon> sideBySide = ChunkPolygons.of(Set.of(chunk(0, 0), chunk(1, 0)));
        assertEquals(1, sideBySide.size());
        assertTrue(sideBySide.get(0).holes().isEmpty());
        assertEquals(
                Set.of(
                        new ChunkPolygons.Point(0, 0),
                        new ChunkPolygons.Point(2, 0),
                        new ChunkPolygons.Point(2, 1),
                        new ChunkPolygons.Point(0, 1)),
                corners(sideBySide.get(0).outer()));

        List<ChunkPolygons.Polygon> diagonal = ChunkPolygons.of(Set.of(chunk(0, 0), chunk(1, 1)));
        assertEquals(2, diagonal.size());
        assertTrue(diagonal.get(0).holes().isEmpty());
        assertTrue(diagonal.get(1).holes().isEmpty());
    }

    @Test
    void emptyInputHasNoPolygons() {
        assertTrue(ChunkPolygons.of(Set.of()).isEmpty());
    }

    private static ChunkRef chunk(int x, int z) {
        return new ChunkRef("minecraft:overworld", x, z);
    }

    private static Set<ChunkPolygons.Point> corners(List<ChunkPolygons.Point> ring) {
        return Set.copyOf(ring);
    }
}
