package com.mrzeyner.colonynations.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class TerminalRulesTest {
    @Test
    void coinsGoToTheReceiverEvenWhenTheyMatchTheGood() {
        assertEquals(TerminalInsert.Target.COIN, TerminalInsert.route(true, true));
        assertEquals(TerminalInsert.Target.COIN, TerminalInsert.route(true, false));
    }

    @Test
    void otherItemsReachTheStockOnlyWhenTheyAreTheGood() {
        assertEquals(TerminalInsert.Target.STOCK, TerminalInsert.route(false, true));
        assertEquals(TerminalInsert.Target.REJECT, TerminalInsert.route(false, false));
    }

    @Test
    void terminalFollowsANewNationAndKeepsItsIdOnWilderness() {
        UUID stored = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID next = UUID.fromString("00000000-0000-0000-0000-000000000002");
        assertEquals(next, TerminalNation.follow(stored, next));
        assertEquals(stored, TerminalNation.follow(stored, null));
        assertNull(TerminalNation.follow(null, null));
    }

    @Test
    void placementRequiresThePlayersOwnLand() {
        assertEquals(TerminalPlacement.Denial.NO_NATION, TerminalPlacement.place(false, false));
        assertEquals(TerminalPlacement.Denial.NOT_YOUR_LAND, TerminalPlacement.place(true, false));
        assertEquals(TerminalPlacement.Denial.OK, TerminalPlacement.place(true, true));
    }

    @Test
    void onlyTheOwnerOrAnOperatorMayBreak() {
        assertTrue(TerminalPlacement.mayBreak(true, false));
        assertTrue(TerminalPlacement.mayBreak(false, true));
        assertFalse(TerminalPlacement.mayBreak(false, false));
    }
}
