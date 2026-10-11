package com.mrzeyner.colonynations.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mrzeyner.colonynations.territory.ChunkRef;

import org.junit.jupiter.api.Test;

class SecessionTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";

    @Test
    void offerLastsUntilTheNextGameDay() {
        assertEquals(5L, Secession.offerExpires(4L));
        assertTrue(Secession.offerOpen(4L, 5L));
        assertFalse(Secession.offerOpen(5L, 5L));
    }

    @Test
    void lowerTaxWithinRadiusIsChosen() {
        UUID near = UUID.fromString("00000000-0000-0000-0000-000000000002");
        Secession.Choice choice = Secession.choose(6, claim(0, 0), List.of(
                neighbor(near, 3, chunk(8, 0))), 8).orElseThrow();

        assertEquals(near, choice.nation());
        assertEquals(3, choice.taxRate());
        assertEquals(8, choice.distance());
    }

    @Test
    void landNineChunksAwayIsTooFar() {
        assertTrue(Secession.choose(6, claim(0, 0), List.of(neighbor(id(1), 1, chunk(9, 0))), 8).isEmpty());
    }

    @Test
    void equalOrHigherTaxIsIgnored() {
        assertTrue(Secession.choose(4, claim(0, 0), List.of(
                neighbor(id(1), 4, chunk(1, 0)),
                neighbor(id(2), 9, chunk(0, 0))), 8).isEmpty());
    }

    @Test
    void lowerTaxWinsOverACloserHigherRate() {
        UUID cheap = id(1);
        UUID close = id(2);
        Secession.Choice choice = Secession.choose(6, claim(0, 0), List.of(
                neighbor(close, 5, chunk(1, 0)),
                neighbor(cheap, 1, chunk(8, 0))), 8).orElseThrow();

        assertEquals(cheap, choice.nation());
    }

    @Test
    void sameRateKeepsTheNearerNation() {
        UUID far = id(1);
        UUID near = id(2);
        Secession.Choice choice = Secession.choose(6, claim(0, 0), List.of(
                neighbor(far, 2, chunk(6, 0)),
                neighbor(near, 2, chunk(2, 0))), 8).orElseThrow();

        assertEquals(near, choice.nation());
        assertEquals(2, choice.distance());
    }

    @Test
    void sameRateAndDistanceKeepsTheSmallerNationId() {
        UUID higher = id(9);
        UUID lower = id(1);
        Secession.Choice choice = Secession.choose(6, claim(0, 0), List.of(
                neighbor(higher, 2, chunk(3, 0)),
                neighbor(lower, 2, chunk(0, 3))), 8).orElseThrow();

        assertEquals(lower, choice.nation());
    }

    @Test
    void anotherDimensionDoesNotCount() {
        assertEquals(-1, Secession.distance(claim(0, 0), Set.of(new ChunkRef(NETHER, 0, 0))));
        assertTrue(Secession.choose(6, claim(0, 0), List.of(
                neighbor(id(1), 1, new ChunkRef(NETHER, 0, 0))), 8).isEmpty());
    }

    @Test
    void overlappingLandHasDistanceZero() {
        assertEquals(0, Secession.distance(claim(4, 5), Set.of(chunk(4, 5))));
    }

    @Test
    void emptyClaimHasNoCandidate() {
        assertTrue(Secession.choose(6, Set.of(), List.of(neighbor(id(1), 1, chunk(0, 0))), 8).isEmpty());
    }

    private static Set<ChunkRef> claim(int x, int z) {
        return Set.of(chunk(x, z));
    }

    private static ChunkRef chunk(int x, int z) {
        return new ChunkRef(OVERWORLD, x, z);
    }

    private static Secession.Neighbor neighbor(UUID nation, int taxRate, ChunkRef land) {
        return new Secession.Neighbor(nation, taxRate, Set.of(land));
    }

    private static UUID id(int tail) {
        return UUID.fromString("00000000-0000-0000-0000-00000000000" + tail);
    }
}
