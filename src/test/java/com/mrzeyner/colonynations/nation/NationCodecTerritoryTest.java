package com.mrzeyner.colonynations.nation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import com.mrzeyner.colonynations.territory.ChunkRef;
import com.mrzeyner.colonynations.territory.TerritoryMap;

import org.junit.jupiter.api.Test;

class NationCodecTerritoryTest {
    @Test
    void version2MigratesToEmptyTerritory() {
        NationRegistry registry = new NationRegistry();
        Nation rome = registry.create("Rome", UUID.randomUUID(), 0x112233, 9L);
        registry.addColony(rome.id(), new CityRef("minecraft:overworld", 4));
        registry.setTreasury(rome.id(), 15L);

        byte[] version2 = NationCodec.writeVersion2(registry);
        NationCodec.Loaded loaded = NationCodec.load(version2);

        assertFalse(loaded.unreadable());
        assertEquals(2, loaded.version());
        assertEquals(registry.byId(rome.id()).orElseThrow(), loaded.registry().byId(rome.id()).orElseThrow());
        assertEquals(0, loaded.territory().count(rome.id()));

        byte[] version3 = NationCodec.write(loaded);
        NationCodec.Loaded migrated = NationCodec.load(version3);
        assertEquals(NationCodec.VERSION, migrated.version());
        assertEquals(registry.byId(rome.id()).orElseThrow(), migrated.registry().byId(rome.id()).orElseThrow());
        assertTrue(migrated.territory().claims().isEmpty());
    }

    @Test
    void territoryRoundTripKeepsClaims() {
        NationRegistry registry = new NationRegistry();
        Nation rome = registry.create("Rome", UUID.randomUUID(), 1, 1L);
        Nation gaul = registry.create("Gaul", UUID.randomUUID(), 2, 2L);
        TerritoryMap territory = new TerritoryMap();
        ChunkRef overworld = new ChunkRef("minecraft:overworld", -4, 8);
        ChunkRef nether = new ChunkRef("minecraft:the_nether", -4, 8);
        territory.claim(nether, gaul.id());
        territory.claim(overworld, rome.id());

        byte[] bytes = NationCodec.write(registry, territory);
        assertArrayEquals(bytes, NationCodec.write(registry, territory));

        NationCodec.Loaded loaded = NationCodec.load(bytes);
        assertEquals(3, loaded.version());
        assertEquals(rome.id(), loaded.territory().owner(overworld).orElseThrow());
        assertEquals(gaul.id(), loaded.territory().owner(nether).orElseThrow());
        assertEquals(registry.byId(rome.id()).orElseThrow(), loaded.registry().byId(rome.id()).orElseThrow());
        assertArrayEquals(bytes, NationCodec.write(loaded));
    }
}
