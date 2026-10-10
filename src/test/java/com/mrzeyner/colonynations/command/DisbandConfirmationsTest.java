package com.mrzeyner.colonynations.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DisbandConfirmationsTest {
    @Test
    void secondCallInsideTheWindowConfirms() {
        DisbandConfirmations confirmations = new DisbandConfirmations();
        assertFalse(confirmations.confirm("player:nation", 100L, 600L));
        assertTrue(confirmations.confirm("player:nation", 700L, 600L));
    }

    @Test
    void expiredWindowStartsOver() {
        DisbandConfirmations confirmations = new DisbandConfirmations();
        assertFalse(confirmations.confirm("player:nation", 100L, 600L));
        assertFalse(confirmations.confirm("player:nation", 701L, 600L));
        assertTrue(confirmations.confirm("player:nation", 1301L, 600L));
    }

    @Test
    void differentKeysDoNotConfirmEachOther() {
        DisbandConfirmations confirmations = new DisbandConfirmations();
        assertFalse(confirmations.confirm("a", 0L, 600L));
        assertFalse(confirmations.confirm("b", 10L, 600L));
        assertTrue(confirmations.confirm("a", 10L, 600L));
    }
}
