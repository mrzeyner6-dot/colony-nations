package com.mrzeyner.colonynations.territory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Merges orthogonally neighboring chunks into polygons. A loop of chunks around empty space
 * produces one outer ring and a hole. Diagonal neighbors stay separate.
 * Ring points are chunk corners: chunk {@code (x, z)} covers {@code [x, x + 1] × [z, z + 1]}.
 */
public final class ChunkPolygons {
    private ChunkPolygons() {
    }

    public record Point(int x, int z) {
    }

    public record Polygon(List<Point> outer, List<List<Point>> holes) {
        public Polygon {
            outer = List.copyOf(outer);
            holes = holes.stream().map(List::copyOf).toList();
        }
    }

    public static List<Polygon> of(Collection<ChunkRef> chunks) {
        if (chunks.isEmpty()) {
            return List.of();
        }
        String dimension = null;
        Set<Long> owned = new HashSet<>();
        for (ChunkRef chunk : chunks) {
            if (dimension == null) {
                dimension = chunk.dimension();
            }
            if (dimension.equals(chunk.dimension())) {
                owned.add(key(chunk.x(), chunk.z()));
            }
        }
        Map<Long, List<Edge>> outgoing = new HashMap<>();
        for (long id : owned) {
            int x = x(id);
            int z = z(id);
            if (!owned.contains(key(x, z + 1))) {
                add(outgoing, new Edge(x, z + 1, x + 1, z + 1));
            }
            if (!owned.contains(key(x + 1, z))) {
                add(outgoing, new Edge(x + 1, z + 1, x + 1, z));
            }
            if (!owned.contains(key(x, z - 1))) {
                add(outgoing, new Edge(x + 1, z, x, z));
            }
            if (!owned.contains(key(x - 1, z))) {
                add(outgoing, new Edge(x, z, x, z + 1));
            }
        }
        List<List<Point>> outers = new ArrayList<>();
        List<List<Point>> holes = new ArrayList<>();
        while (!outgoing.isEmpty()) {
            List<Point> ring = trace(outgoing);
            ring = simplify(ring);
            if (ring.size() < 3) {
                continue;
            }
            if (signedArea(ring) < 0) {
                outers.add(ring);
            } else if (signedArea(ring) > 0) {
                holes.add(ring);
            }
        }
        List<Polygon> polygons = new ArrayList<>();
        boolean[] used = new boolean[holes.size()];
        for (List<Point> outer : outers) {
            List<List<Point>> inside = new ArrayList<>();
            long outerArea = Math.abs(signedArea(outer));
            for (int i = 0; i < holes.size(); i++) {
                if (used[i] || Math.abs(signedArea(holes.get(i))) >= outerArea) {
                    continue;
                }
                double[] sample = interior(holes.get(i));
                if (contains(outer, sample[0], sample[1])) {
                    used[i] = true;
                    inside.add(holes.get(i));
                }
            }
            polygons.add(new Polygon(outer, inside));
        }
        return List.copyOf(polygons);
    }

    /**
     * State-land polygons. {@code blocked} chunks, such as a city claim, are not part of the fill:
     * they are removed from the input and, when the state ring encloses them, cut out as holes.
     */
    public static List<Polygon> excluding(Collection<ChunkRef> state, Collection<ChunkRef> blocked) {
        if (state == null || state.isEmpty()) {
            return List.of();
        }
        Set<ChunkRef> owned = new HashSet<>();
        Set<ChunkRef> holes = new HashSet<>();
        String dimension = null;
        for (ChunkRef chunk : state) {
            if (chunk == null) {
                continue;
            }
            if (dimension == null) {
                dimension = chunk.dimension();
            }
            if (dimension.equals(chunk.dimension())) {
                owned.add(chunk);
            }
        }
        if (blocked != null) {
            for (ChunkRef chunk : blocked) {
                if (chunk != null && dimension.equals(chunk.dimension())) {
                    owned.remove(chunk);
                    holes.add(chunk);
                }
            }
        }
        List<Polygon> polygons = new ArrayList<>();
        for (Polygon polygon : of(owned)) {
            Set<ChunkRef> inside = new HashSet<>();
            for (ChunkRef chunk : holes) {
                if (covers(polygon, chunk.x() + 0.5, chunk.z() + 0.5)) {
                    inside.add(chunk);
                }
            }
            if (inside.isEmpty()) {
                polygons.add(polygon);
                continue;
            }
            List<List<Point>> extra = new ArrayList<>(polygon.holes());
            for (Polygon claim : of(inside)) {
                extra.add(asHole(claim.outer()));
            }
            polygons.add(new Polygon(polygon.outer(), extra));
        }
        return List.copyOf(polygons);
    }

    /**
     * Whether the chunk-coordinate point lies in the outer ring and outside every hole.
     */
    public static boolean covers(Polygon polygon, double x, double z) {
        if (polygon == null || !contains(polygon.outer(), x, z)) {
            return false;
        }
        for (List<Point> hole : polygon.holes()) {
            if (contains(hole, x, z)) {
                return false;
            }
        }
        return true;
    }

    private static List<Point> asHole(List<Point> ring) {
        if (signedArea(ring) < 0) {
            List<Point> reversed = new ArrayList<>(ring);
            Collections.reverse(reversed);
            return reversed;
        }
        return ring;
    }

    private static List<Point> trace(Map<Long, List<Edge>> outgoing) {
        long startKey = outgoing.keySet().iterator().next();
        Edge edge = outgoing.get(startKey).get(0);
        Point origin = new Point(edge.x1, edge.z1);
        List<Point> ring = new ArrayList<>();
        int guard = 0;
        while (guard++ < 100000) {
            ring.add(new Point(edge.x1, edge.z1));
            remove(outgoing, edge);
            if (edge.x2 == origin.x && edge.z2 == origin.z) {
                break;
            }
            List<Edge> next = outgoing.get(key(edge.x2, edge.z2));
            if (next == null || next.isEmpty()) {
                break;
            }
            edge = choose(edge, next);
        }
        return ring;
    }

    private static Edge choose(Edge incoming, List<Edge> options) {
        int dx = incoming.x2 - incoming.x1;
        int dz = incoming.z2 - incoming.z1;
        int leftX = dz;
        int leftZ = -dx;
        Edge best = options.get(0);
        int bestDot = Integer.MIN_VALUE;
        for (Edge option : options) {
            int ox = option.x2 - option.x1;
            int oz = option.z2 - option.z1;
            int dot = ox * leftX + oz * leftZ;
            if (dot > bestDot) {
                best = option;
                bestDot = dot;
            }
        }
        return best;
    }

    private static List<Point> simplify(List<Point> ring) {
        if (ring.size() < 3) {
            return ring;
        }
        List<Point> points = new ArrayList<>(ring);
        boolean removed = true;
        while (removed && points.size() >= 3) {
            removed = false;
            for (int i = 0; i < points.size(); i++) {
                Point previous = points.get((i + points.size() - 1) % points.size());
                Point current = points.get(i);
                Point next = points.get((i + 1) % points.size());
                if (sameDirection(previous, current, next)) {
                    points.remove(i);
                    removed = true;
                    break;
                }
            }
        }
        return points;
    }

    private static boolean sameDirection(Point a, Point b, Point c) {
        int abx = b.x - a.x;
        int abz = b.z - a.z;
        int bcx = c.x - b.x;
        int bcz = c.z - b.z;
        return abx * bcz - abz * bcx == 0 && abx * bcx + abz * bcz > 0;
    }

    private static long signedArea(List<Point> ring) {
        long area = 0;
        for (int i = 0; i < ring.size(); i++) {
            Point current = ring.get(i);
            Point next = ring.get((i + 1) % ring.size());
            area += (long) current.x * next.z - (long) next.x * current.z;
        }
        return area;
    }

    /**
     * A point just inside a clockwise hole, on the right of its first edge.
     */
    private static double[] interior(List<Point> ring) {
        Point a = ring.get(0);
        Point b = ring.get(1);
        double dx = b.x - a.x;
        double dz = b.z - a.z;
        double length = Math.hypot(dx, dz);
        return new double[] {(a.x + b.x) / 2.0 + (-dz / length) * 0.25, (a.z + b.z) / 2.0 + (dx / length) * 0.25};
    }

    private static boolean contains(List<Point> ring, double x, double z) {
        boolean inside = false;
        for (int i = 0, j = ring.size() - 1; i < ring.size(); j = i++) {
            double xi = ring.get(i).x;
            double zi = ring.get(i).z;
            double xj = ring.get(j).x;
            double zj = ring.get(j).z;
            boolean crosses = (zi > z) != (zj > z) && x < (xj - xi) * (z - zi) / (zj - zi) + xi;
            if (crosses) {
                inside = !inside;
            }
        }
        return inside;
    }

    private static void add(Map<Long, List<Edge>> outgoing, Edge edge) {
        outgoing.computeIfAbsent(key(edge.x1, edge.z1), key -> new ArrayList<>()).add(edge);
    }

    private static void remove(Map<Long, List<Edge>> outgoing, Edge edge) {
        List<Edge> edges = outgoing.get(key(edge.x1, edge.z1));
        if (edges == null) {
            return;
        }
        edges.remove(edge);
        if (edges.isEmpty()) {
            outgoing.remove(key(edge.x1, edge.z1));
        }
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private static int x(long key) {
        return (int) (key >> 32);
    }

    private static int z(long key) {
        return (int) key;
    }

    private record Edge(int x1, int z1, int x2, int z2) {
    }
}
