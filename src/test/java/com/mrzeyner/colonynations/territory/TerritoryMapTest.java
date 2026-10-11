package com.mrzeyner.colonynations.territory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mrzeyner.colonynations.nation.Nation;
import com.mrzeyner.colonynations.nation.NationCodec;
import com.mrzeyner.colonynations.nation.NationRegistry;

class TerritoryMapTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";

    @Test
    void claimReleaseAndCount() {
        TerritoryMap map = new TerritoryMap();
        UUID rome = UUID.randomUUID();
        ChunkRef home = chunk(OVERWORLD, 3, -2);

        map.claim(home, rome);
        map.claim(home, rome);

        assertEquals(rome, map.owner(home).orElseThrow());
        assertEquals(1, map.count(rome));
        assertEquals(1, map.chunks(rome).size());
        assertTrue(map.chunks(rome).contains(home));

        map.release(home);
        map.release(home);

        assertTrue(map.owner(home).isEmpty());
        assertEquals(0, map.count(rome));
        assertTrue(map.chunks(rome).isEmpty());
        assertTrue(map.allChunks().isEmpty());
    }

    @Test
    void secondNationCannotClaimTheSameChunk() {
        TerritoryMap map = new TerritoryMap();
        UUID rome = UUID.randomUUID();
        UUID gaul = UUID.randomUUID();
        ChunkRef home = chunk(OVERWORLD, 0, 0);
        map.claim(home, rome);

        TerritoryException exception = assertThrows(TerritoryException.class, () -> map.claim(home, gaul));
        assertEquals(TerritoryException.Reason.ALREADY_CLAIMED, exception.reason());
        assertEquals(rome, map.owner(home).orElseThrow());
        assertEquals(0, map.count(gaul));
    }

    @Test
    void adjacencyIsFourNeighboursInTheSameDimension() {
        TerritoryMap map = new TerritoryMap();
        UUID rome = UUID.randomUUID();
        UUID gaul = UUID.randomUUID();
        ChunkRef home = chunk(OVERWORLD, 4, 5);
        map.claim(home, rome);
        map.claim(chunk(OVERWORLD, 9, 9), gaul);

        assertTrue(map.isAdjacentToNation(chunk(OVERWORLD, 5, 5), rome));
        assertTrue(map.isAdjacentToNation(chunk(OVERWORLD, 3, 5), rome));
        assertTrue(map.isAdjacentToNation(chunk(OVERWORLD, 4, 6), rome));
        assertTrue(map.isAdjacentToNation(chunk(OVERWORLD, 4, 4), rome));

        assertFalse(map.isAdjacentToNation(chunk(OVERWORLD, 5, 6), rome));
        assertFalse(map.isAdjacentToNation(home, rome));
        assertFalse(map.isAdjacentToNation(chunk(NETHER, 5, 5), rome));
        assertFalse(map.isAdjacentToNation(chunk(OVERWORLD, 10, 9), rome));
        assertTrue(map.isAdjacentToNation(chunk(OVERWORLD, 10, 9), gaul));
        assertFalse(map.isAdjacentToNation(chunk(OVERWORLD, 0, 0), rome));
    }

    @Test
    void releaseAllFreesOnlyThatNation() {
        TerritoryMap map = new TerritoryMap();
        UUID rome = UUID.randomUUID();
        UUID gaul = UUID.randomUUID();
        ChunkRef romeChunk = chunk(OVERWORLD, 1, 1);
        ChunkRef gaulChunk = chunk(OVERWORLD, 8, 8);
        map.claim(romeChunk, rome);
        map.claim(chunk(NETHER, 1, 1), rome);
        map.claim(gaulChunk, gaul);

        map.releaseAll(rome);
        map.releaseAll(UUID.randomUUID());

        assertTrue(map.owner(romeChunk).isEmpty());
        assertEquals(0, map.count(rome));
        assertEquals(gaul, map.owner(gaulChunk).orElseThrow());
        assertEquals(1, map.count(gaul));
    }

    @Test
    void codecRoundTripKeepsNationsAndTerritory() {
        NationRegistry registry = new NationRegistry();
        Nation rome = registry.create("Rome", UUID.randomUUID(), 0x112233, 4L);
        registry.setTreasury(rome.id(), 15L);
        TerritoryMap territory = new TerritoryMap();
        territory.claim(chunk(OVERWORLD, -3, 4), rome.id());
        territory.claim(chunk(NETHER, 0, 0), rome.id());
        territory.claim(chunk(OVERWORLD, 2, 2), rome.id());

        NationCodec.Loaded loaded = NationCodec.load(NationCodec.write(registry, territory));

        assertEquals(NationCodec.VERSION, loaded.version());
        assertEquals(registry.byId(rome.id()).orElseThrow(), loaded.registry().byId(rome.id()).orElseThrow());
        assertEquals(territory.allChunks(), loaded.territory().allChunks());
        assertEquals(rome.id(), loaded.territory().owner(chunk(OVERWORLD, -3, 4)).orElseThrow());
        assertEquals(rome.id(), loaded.territory().owner(chunk(NETHER, 0, 0)).orElseThrow());
        assertEquals(3, loaded.territory().count(rome.id()));
    }

    private static ChunkRef chunk(String dimension, int x, int z) {
        return new ChunkRef(dimension, x, z);
    }
}
