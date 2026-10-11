package com.mrzeyner.colonynations.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class TerminalStationTest {
    private static final UUID SELLER = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BUYER = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    @Test
    void noStationInRangeDoesNotTrade() {
        TerminalStation.Binding binding = TerminalStation.bind(0, 0, 0, List.of(
                sight(4, 0, 0, "Far", true, "Express", PLAYER, BUYER)), 3, SELLER);
        assertFalse(binding.station());
        assertFalse(binding.trades());
        assertNull(binding.buyer());
    }

    @Test
    void emptyStationIsShownAndDoesNotTakeCoins() {
        TerminalStation.Binding binding = TerminalStation.bind(0, 0, 0, List.of(
                sight(1, 0, 0, "Halt", false, "", null, null)), 3, SELLER);
        assertTrue(binding.station());
        assertEquals("Halt", binding.stationName());
        assertFalse(binding.train());
        assertFalse(binding.trades());
    }

    @Test
    void fartherTrainBeatsACloserEmptyStation() {
        TerminalStation.Binding binding = TerminalStation.bind(0, 0, 0, List.of(
                sight(1, 0, 0, "Empty", false, "", null, null),
                sight(0, 3, 0, "Busy", true, "North", PLAYER, BUYER)), 3, SELLER);
        assertEquals("Busy", binding.stationName());
        assertEquals("North", binding.trainName());
        assertEquals(TerminalStation.Kind.NATION, binding.kind());
        assertEquals(BUYER, binding.buyer());
    }

    @Test
    void nearestTrainWinsAndTiesPreferTheLowerPosition() {
        TerminalStation.Sighting west = sight(2, 0, 0, "West", true, "W", PLAYER, BUYER);
        TerminalStation.Sighting south = sight(0, 2, 0, "South", true, "S", PLAYER, BUYER);
        TerminalStation.Binding lowerX = TerminalStation.bind(0, 0, 0, List.of(west, south), 3, SELLER);
        assertEquals("South", lowerX.stationName());

        TerminalStation.Sighting diag = sight(2, 2, 0, "Diag", true, "D", PLAYER, BUYER);
        TerminalStation.Sighting axis = sight(2, 0, 0, "Axis", true, "A", PLAYER, BUYER);
        TerminalStation.Binding nearerEuclid = TerminalStation.bind(0, 0, 0, List.of(diag, axis), 3, SELLER);
        assertEquals("Axis", nearerEuclid.stationName());
    }

    @Test
    void sellersOwnTrainDoesNotBuy() {
        TerminalStation.Binding binding = TerminalStation.bind(0, 0, 0, List.of(
                sight(1, 1, 0, "Home", true, "Local", PLAYER, SELLER)), 3, SELLER);
        assertEquals(TerminalStation.Kind.OWN, binding.kind());
        assertEquals("Local", binding.trainName());
        assertEquals(SELLER, binding.ownerNation());
        assertFalse(binding.trades());
        assertNull(binding.buyer());
    }

    @Test
    void ownerWithoutANationIsAPrivateBuyer() {
        TerminalStation.Binding binding = TerminalStation.bind(0, 0, 0, List.of(
                sight(1, 0, 0, "Halt", true, "Wagon", PLAYER, null)), 3, SELLER);
        assertEquals(TerminalStation.Kind.PRIVATE, binding.kind());
        assertEquals(PLAYER, binding.buyer());
        assertTrue(binding.trades());
    }

    private static TerminalStation.Sighting sight(
            int x, int y, int z, String station, boolean train, String trainName, UUID owner, UUID nation) {
        return new TerminalStation.Sighting(x, y, z, station, train, trainName, owner, nation);
    }

    @Test
    void trainWithoutAnOwnerUsesTheUnownedBucket() {
        TerminalStation.Binding binding = TerminalStation.bind(0, 0, 0, List.of(
                sight(0, 0, 1, "Halt", true, "Ghost", null, null)), 3, SELLER);
        assertEquals(TerminalStation.Kind.PRIVATE, binding.kind());
        assertEquals(TerminalTrade.UNOWNED, binding.buyer());
    }
}
