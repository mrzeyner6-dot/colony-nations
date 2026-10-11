package com.mrzeyner.colonynations.territory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Merges orthogonally adjacent chunks into polygon outlines, including holes.
 * Points are chunk-corner indices: chunk {@code (x, z)} covers the square from
 * {@code (x, z)} to {@code (x + 1, z + 1)}, with {@code z} increasing south.
 * Rings start at the south-west corner and keep the filled side on the left
 * (south-west, south-east, north-east, north-west), which matches JourneyMap.
 */
public final class ChunkOutlines {
    private ChunkOutlines() {
    }

    public record Point(int x, int z) {
    }

    /** A cell on the chunk grid, not a corner. */
    public record GridChunk(int x, int z) {
    }

    /** Ring points are not closed; the last point joins the first. */
    public record Ring(List<Point> points) {
        public Ring {
            points = List.copyOf(points);
        }
    }

    public record Polygon(Ring outer, List<Ring> holes) {
        public Polygon {
            holes = List.copyOf(holes);
        }
    }

    public static List<Polygon> merge(Set<GridChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        List<Polygon> polygons = new ArrayList<>();
        for (Set<GridChunk> component : components(chunks)) {
            polygons.addAll(polygonsOf(component));
        }
        polygons.sort(Comparator
                .comparingInt((Polygon polygon) -> polygon.outer().points().get(0).x())
                .thenComparingInt(polygon -> polygon.outer().points().get(0).z()));
        return List.copyOf(polygons);
    }

    private static List<Set<GridChunk>> components(Set<GridChunk> chunks) {
        Set<GridChunk> remaining = new HashSet<>(chunks);
        List<Set<GridChunk>> components = new ArrayList<>();
        while (!remaining.isEmpty()) {
            GridChunk start = remaining.iterator().next();
            remaining.remove(start);
            Set<GridChunk> component = new HashSet<>();
            ArrayDeque<GridChunk> queue = new ArrayDeque<>();
            queue.add(start);
            while (!queue.isEmpty()) {
                GridChunk cell = queue.removeFirst();
                component.add(cell);
                for (GridChunk next : orthogonal(cell)) {
                    if (remaining.remove(next)) {
                        queue.add(next);
                    }
                }
            }
            components.add(component);
        }
        return components;
    }

    private static List<GridChunk> orthogonal(GridChunk cell) {
        return List.of(
                new GridChunk(cell.x() + 1, cell.z()),
                new GridChunk(cell.x() - 1, cell.z()),
                new GridChunk(cell.x(), cell.z() + 1),
                new GridChunk(cell.x(), cell.z() - 1));
    }

    private static List<Polygon> polygonsOf(Set<GridChunk> component) {
        Map<Point, List<Point>> outgoing = new HashMap<>();
        for (GridChunk cell : component) {
            int x = cell.x();
            int z = cell.z();
            if (!component.contains(new GridChunk(x, z + 1))) {
                link(outgoing, new Point(x, z + 1), new Point(x + 1, z + 1));
            }
            if (!component.contains(new GridChunk(x + 1, z))) {
                link(outgoing, new Point(x + 1, z + 1), new Point(x + 1, z));
            }
            if (!component.contains(new GridChunk(x, z - 1))) {
                link(outgoing, new Point(x + 1, z), new Point(x, z));
            }
            if (!component.contains(new GridChunk(x - 1, z))) {
                link(outgoing, new Point(x, z), new Point(x, z + 1));
            }
        }
        List<Ring> outers = new ArrayList<>();
        List<Ring> holes = new ArrayList<>();
        while (hasEdge(outgoing)) {
            List<Point> loop = walk(outgoing);
            if (loop.size() < 3) {
                continue;
            }
            List<Point> simplified = simplify(loop);
            if (simplified.size() < 3) {
                continue;
            }
            List<Point> ring = rotateSouthWest(simplified);
            if (shoelace(ring) < 0) {
                outers.add(new Ring(ring));
            } else {
                holes.add(new Ring(ring));
            }
        }
        holes.sort(Comparator
                .comparingInt((Ring ring) -> ring.points().get(0).x())
                .thenComparingInt(ring -> ring.points().get(0).z()));
        if (outers.isEmpty()) {
            return List.of();
        }
        List<Polygon> polygons = new ArrayList<>();
        if (outers.size() == 1) {
            polygons.add(new Polygon(outers.get(0), holes));
            return polygons;
        }
        for (Ring outer : outers) {
            List<Ring> owned = new ArrayList<>();
            for (Ring hole : holes) {
                if (contains(outer, hole.points().get(0))) {
                    owned.add(hole);
                }
            }
            polygons.add(new Polygon(outer, owned));
        }
        return polygons;
    }

    private static void link(Map<Point, List<Point>> outgoing, Point from, Point to) {
        outgoing.computeIfAbsent(from, key -> new ArrayList<>()).add(to);
    }

    private static boolean hasEdge(Map<Point, List<Point>> outgoing) {
        for (List<Point> next : outgoing.values()) {
            if (!next.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static List<Point> walk(Map<Point, List<Point>> outgoing) {
        Point start = null;
        for (Map.Entry<Point, List<Point>> entry : outgoing.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                start = entry.getKey();
                break;
            }
        }
        List<Point> loop = new ArrayList<>();
        if (start == null) {
            return loop;
        }
        Point current = start;
        int guard = 0;
        int limit = outgoing.size() * 4 + 4;
        do {
            List<Point> next = outgoing.get(current);
            if (next == null || next.isEmpty()) {
                break;
            }
            Point following = next.remove(next.size() - 1);
            loop.add(current);
            current = following;
            guard++;
        } while (!current.equals(start) && guard <= limit);
        return loop;
    }

    private static List<Point> simplify(List<Point> ring) {
        if (ring.size() <= 3) {
            return ring;
        }
        List<Point> simplified = new ArrayList<>();
        int count = ring.size();
        for (int i = 0; i < count; i++) {
            Point previous = ring.get((i - 1 + count) % count);
            Point current = ring.get(i);
            Point next = ring.get((i + 1) % count);
            boolean sameColumn = previous.x() == current.x() && current.x() == next.x();
            boolean sameRow = previous.z() == current.z() && current.z() == next.z();
            if (sameColumn || sameRow) {
                continue;
            }
            simplified.add(current);
        }
        return simplified.size() >= 3 ? simplified : ring;
    }

    /** South-west corner: smallest x, then largest z. */
    private static List<Point> rotateSouthWest(List<Point> ring) {
        int start = 0;
        for (int i = 1; i < ring.size(); i++) {
            Point point = ring.get(i);
            Point chosen = ring.get(start);
            if (point.x() < chosen.x() || (point.x() == chosen.x() && point.z() > chosen.z())) {
                start = i;
            }
        }
        if (start == 0) {
            return ring;
        }
        List<Point> rotated = new ArrayList<>(ring.size());
        for (int i = 0; i < ring.size(); i++) {
            rotated.add(ring.get((start + i) % ring.size()));
        }
        return rotated;
    }

    /** Negative when the ring is an outer boundary in this winding. */
    private static long shoelace(List<Point> ring) {
        long sum = 0;
        int count = ring.size();
        for (int i = 0; i < count; i++) {
            Point current = ring.get(i);
            Point next = ring.get((i + 1) % count);
            sum += (long) current.x() * next.z() - (long) next.x() * current.z();
        }
        return sum;
    }

    private static boolean contains(Ring outer, Point point) {
        boolean inside = false;
        List<Point> points = outer.points();
        int count = points.size();
        for (int i = 0, j = count - 1; i < count; j = i++) {
            Point current = points.get(i);
            Point previous = points.get(j);
            boolean crosses = (current.z() > point.z()) != (previous.z() > point.z());
            if (!crosses) {
                continue;
            }
            long left = (long) (previous.x() - current.x()) * (point.z() - current.z());
            long right = (long) (point.x() - current.x()) * (previous.z() - current.z());
            if (left > right) {
                inside = !inside;
            }
        }
        return inside;
    }
}
