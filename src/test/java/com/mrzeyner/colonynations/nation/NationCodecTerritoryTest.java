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
        assertEquals(Nation.DEFAULT_TAX_RATE, migrated.registry().byId(rome.id()).orElseThrow().taxRate());
        assertEquals(null, migrated.registry().byId(rome.id()).orElseThrow().lastTaxChangeDay());
    }

    @Test
    void version3DropsATaxRateWrittenOnlyInMemory() {
        NationRegistry registry = new NationRegistry();
        Nation rome = registry.create("Rome", UUID.randomUUID(), 0x112233, 9L);
        registry.addColony(rome.id(), new CityRef("minecraft:overworld", 4));
        registry.setTreasury(rome.id(), 15L);
        registry.setTaxRate(rome.id(), 5, 3L);
        TerritoryMap territory = new TerritoryMap();
        ChunkRef chunk = new ChunkRef("minecraft:overworld", 1, 2);
        territory.claim(chunk, rome.id());

        NationCodec.Loaded loaded = NationCodec.load(NationCodec.writeVersion3(registry, territory));

        assertEquals(3, loaded.version());
        Nation migrated = loaded.registry().byId(rome.id()).orElseThrow();
        assertEquals(Nation.DEFAULT_TAX_RATE, migrated.taxRate());
        assertEquals(null, migrated.lastTaxChangeDay());
        assertEquals(15L, migrated.treasury());
        assertEquals(rome.id(), loaded.territory().owner(chunk).orElseThrow());

        NationCodec.Loaded again = NationCodec.load(NationCodec.write(loaded));
        assertEquals(NationCodec.VERSION, again.version());
        assertEquals(migrated, again.registry().byId(rome.id()).orElseThrow());
        assertEquals(rome.id(), again.territory().owner(chunk).orElseThrow());
    }

    @Test
    void version4KeepsTaxAndStartsSeparatismAtZero() {
        NationRegistry registry = new NationRegistry();
        Nation rome = registry.create("Rome", UUID.randomUUID(), 1, 1L);
        CityRef city = new CityRef("minecraft:overworld", 4);
        registry.addColony(rome.id(), city);
        registry.setTaxRate(rome.id(), 5, 3L);
        registry.setSeparatism(rome.id(), city, 40.0D);

        NationCodec.Loaded loaded = NationCodec.load(NationCodec.writeVersion4(registry, new TerritoryMap()));

        assertEquals(4, loaded.version());
        Nation migrated = loaded.registry().byId(rome.id()).orElseThrow();
        assertEquals(5, migrated.taxRate());
        assertEquals(3L, migrated.lastTaxChangeDay());
        assertEquals(0.0D, migrated.separatism(city), 0.0D);

        NationCodec.Loaded again = NationCodec.load(NationCodec.write(loaded));
        assertEquals(NationCodec.VERSION, again.version());
        assertEquals(migrated, again.registry().byId(rome.id()).orElseThrow());
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
        assertEquals(NationCodec.VERSION, loaded.version());
        assertEquals(rome.id(), loaded.territory().owner(overworld).orElseThrow());
        assertEquals(gaul.id(), loaded.territory().owner(nether).orElseThrow());
        assertEquals(registry.byId(rome.id()).orElseThrow(), loaded.registry().byId(rome.id()).orElseThrow());
        assertArrayEquals(bytes, NationCodec.write(loaded));
    }
}
