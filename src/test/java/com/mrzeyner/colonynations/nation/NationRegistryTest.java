package com.mrzeyner.colonynations.nation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class NationRegistryTest {
    @Test
    void createRejectsShortAndLongNames() {
        NationRegistry registry = new NationRegistry();
        assertReason(NationException.Reason.NAME_LENGTH, () -> registry.create("ab", UUID.randomUUID(), 0, 1L));
        assertReason(NationException.Reason.NAME_LENGTH, () -> registry.create("  x  ", UUID.randomUUID(), 0, 1L));
        assertReason(NationException.Reason.NAME_LENGTH, () -> registry.create("n".repeat(25), UUID.randomUUID(), 0, 1L));
        assertReason(NationException.Reason.NAME_LENGTH, () -> registry.create(null, UUID.randomUUID(), 0, 1L));
    }

    @Test
    void createAcceptsBoundsAndTrims() {
        NationRegistry registry = new NationRegistry();
        Nation nation = registry.create("  Київ  ", UUID.randomUUID(), 0x336699, 10L);
        assertEquals("Київ", nation.name());
        Nation longName = registry.create("n".repeat(24), UUID.randomUUID(), 1, 2L);
        assertEquals(24, longName.name().length());
    }

    @Test
    void nameIsUniqueIgnoringCase() {
        NationRegistry registry = new NationRegistry();
        UUID owner = UUID.randomUUID();
        Nation nation = registry.create("Rome", owner, 1, 1L);
        assertReason(NationException.Reason.NAME_TAKEN, () -> registry.create("rome", UUID.randomUUID(), 1, 1L));
        assertReason(NationException.Reason.NAME_TAKEN, () -> registry.create("  ROME ", UUID.randomUUID(), 1, 1L));

        Nation renamed = registry.rename(nation.id(), "roma");
        assertEquals("roma", renamed.name());
        assertEquals(nation.id(), registry.byName("ROMA").orElseThrow().id());
        assertTrue(registry.byName("Rome").isEmpty());

        registry.rename(nation.id(), "Roma");
        assertEquals("Roma", registry.byId(nation.id()).orElseThrow().name());
    }

    @Test
    void onePlayerOwnsOneNation() {
        NationRegistry registry = new NationRegistry();
        UUID owner = UUID.randomUUID();
        Nation nation = registry.create("Rome", owner, 1, 1L);
        assertReason(NationException.Reason.OWNER_HAS_NATION, () -> registry.create("Gaul", owner, 2, 2L));
        assertEquals(nation.id(), registry.byOwner(owner).orElseThrow().id());
    }

    @Test
    void colonyBelongsToOneNationAndFirstColonyIsCapital() {
        NationRegistry registry = new NationRegistry();
        Nation rome = registry.create("Rome", UUID.randomUUID(), 1, 1L);
        Nation gaul = registry.create("Gaul", UUID.randomUUID(), 2, 2L);

        rome = registry.addColony(rome.id(), 10);
        assertEquals(10, rome.capitalColonyId());
        rome = registry.addColony(rome.id(), 4);
        assertEquals(10, rome.capitalColonyId());
        assertEquals(rome.id(), registry.byColony(4).orElseThrow().id());

        assertReason(NationException.Reason.COLONY_TAKEN, () -> registry.addColony(gaul.id(), 10));
        registry.addColony(rome.id(), 4);
        assertEquals(2, registry.byId(rome.id()).orElseThrow().colonyIds().size());
    }

    @Test
    void removingCapitalPicksSmallestRemainingId() {
        NationRegistry registry = new NationRegistry();
        Nation rome = registry.create("Rome", UUID.randomUUID(), 1, 1L);
        UUID id = rome.id();
        registry.addColony(id, 10);
        registry.addColony(id, 3);
        registry.addColony(id, 7);

        Nation after = registry.removeColony(id, 10);
        assertEquals(3, after.capitalColonyId());

        Nation kept = registry.removeColony(id, 3);
        assertEquals(7, kept.capitalColonyId());

        Nation empty = registry.removeColony(id, 7);
        assertEquals(null, empty.capitalColonyId());
        assertTrue(empty.colonyIds().isEmpty());
        assertTrue(registry.byColony(7).isEmpty());
    }

    @Test
    void deleteFreesOwnerNameAndColonies() {
        NationRegistry registry = new NationRegistry();
        UUID owner = UUID.randomUUID();
        Nation rome = registry.create("Rome", owner, 1, 1L);
        registry.addColony(rome.id(), 5);
        registry.delete(rome.id());

        assertTrue(registry.byId(rome.id()).isEmpty());
        assertTrue(registry.byName("rome").isEmpty());
        assertTrue(registry.byOwner(owner).isEmpty());
        assertTrue(registry.byColony(5).isEmpty());

        Nation again = registry.create("Rome", owner, 2, 3L);
        again = registry.addColony(again.id(), 5);
        assertEquals(5, again.capitalColonyId());
    }

    @Test
    void setCapitalMustBeAMemberColony() {
        NationRegistry registry = new NationRegistry();
        Nation rome = registry.create("Rome", UUID.randomUUID(), 1, 1L);
        registry.addColony(rome.id(), 4);
        registry.addColony(rome.id(), 9);
        assertEquals(4, registry.byId(rome.id()).orElseThrow().capitalColonyId());

        Nation moved = registry.setCapital(rome.id(), 9);
        assertEquals(9, moved.capitalColonyId());
        assertReason(NationException.Reason.UNKNOWN_COLONY, () -> registry.setCapital(rome.id(), 1));
    }

    @Test
    void unknownNationAndColony() {
        NationRegistry registry = new NationRegistry();
        UUID missing = UUID.randomUUID();
        assertReason(NationException.Reason.UNKNOWN_NATION, () -> registry.delete(missing));
        assertReason(NationException.Reason.UNKNOWN_NATION, () -> registry.rename(missing, "Rome"));
        assertReason(NationException.Reason.UNKNOWN_NATION, () -> registry.addColony(missing, 1));
        Nation rome = registry.create("Rome", UUID.randomUUID(), 1, 1L);
        assertReason(NationException.Reason.UNKNOWN_COLONY, () -> registry.removeColony(rome.id(), 9));
    }

    @Test
    void serializationRoundTripKeepsEveryField() {
        NationRegistry registry = new NationRegistry();
        Nation rome = registry.create("Rome", UUID.randomUUID(), 0x112233, 100L);
        registry.setTreasury(rome.id(), 42L);
        registry.addColony(rome.id(), 10);
        registry.addColony(rome.id(), 2);
        registry.addColony(rome.id(), 8);

        Nation empty = registry.create("Gaul", UUID.randomUUID(), 0, 7L);
        registry.setColor(empty.id(), 0xABCDEF);

        NationRegistry loaded = NationCodec.read(NationCodec.write(registry));

        assertEquals(registry.byId(rome.id()).orElseThrow(), loaded.byId(rome.id()).orElseThrow());
        assertEquals(registry.byId(empty.id()).orElseThrow(), loaded.byId(empty.id()).orElseThrow());
        assertEquals(10, loaded.byId(rome.id()).orElseThrow().capitalColonyId());
        assertEquals(null, loaded.byId(empty.id()).orElseThrow().capitalColonyId());
        assertEquals(42L, loaded.byId(rome.id()).orElseThrow().treasury());
        assertEquals(0xABCDEF, loaded.byName("gaul").orElseThrow().color());
        assertEquals(rome.owner(), loaded.byOwner(rome.owner()).orElseThrow().owner());
        assertEquals(100L, loaded.byId(rome.id()).orElseThrow().createdGameTime());
        assertEquals(2, loaded.all().size());
    }

    private static void assertReason(NationException.Reason reason, Runnable action) {
        NationException exception = assertThrows(NationException.class, action::run);
        assertEquals(reason, exception.reason());
    }
}
