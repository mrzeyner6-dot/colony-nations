package com.mrzeyner.colonynations.territory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.mrzeyner.colonynations.territory.ChunkOutlines.GridChunk;
import com.mrzeyner.colonynations.territory.ChunkOutlines.Point;
import com.mrzeyner.colonynations.territory.ChunkOutlines.Polygon;

class ChunkOutlinesTest {
    @Test
    void emptySetHasNoPolygons() {
        assertTrue(ChunkOutlines.merge(Set.of()).isEmpty());
    }

    @Test
    void singleChunkIsASquareStartingSouthWest() {
        List<Polygon> polygons = ChunkOutlines.merge(Set.of(chunk(0, 0)));

        assertEquals(1, polygons.size());
        assertTrue(polygons.get(0).holes().isEmpty());
        assertEquals(List.of(point(0, 1), point(1, 1), point(1, 0), point(0, 0)), polygons.get(0).outer().points());
    }

    @Test
    void adjacentChunksMergeIntoOneRectangle() {
        List<Polygon> polygons = ChunkOutlines.merge(Set.of(chunk(0, 0), chunk(1, 0)));

        assertEquals(1, polygons.size());
        assertEquals(List.of(point(0, 1), point(2, 1), point(2, 0), point(0, 0)), polygons.get(0).outer().points());
    }

    @Test
    void diagonalChunksStaySeparate() {
        List<Polygon> polygons = ChunkOutlines.merge(Set.of(chunk(0, 0), chunk(1, 1)));

        assertEquals(2, polygons.size());
        assertEquals(point(0, 1), polygons.get(0).outer().points().get(0));
        assertEquals(point(1, 2), polygons.get(1).outer().points().get(0));
    }

    @Test
    void enclosedGapIsAHole() {
        Set<GridChunk> donut = new java.util.HashSet<>();
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                if (x != 1 || z != 1) {
                    donut.add(chunk(x, z));
                }
            }
        }
        List<Polygon> polygons = ChunkOutlines.merge(donut);

        assertEquals(1, polygons.size());
        assertEquals(List.of(point(0, 3), point(3, 3), point(3, 0), point(0, 0)), polygons.get(0).outer().points());
        assertEquals(1, polygons.get(0).holes().size());
        assertEquals(List.of(point(1, 2), point(1, 1), point(2, 1), point(2, 2)), polygons.get(0).holes().get(0).points());
    }

    private static GridChunk chunk(int x, int z) {
        return new GridChunk(x, z);
    }

    private static Point point(int x, int z) {
        return new Point(x, z);
    }
}
