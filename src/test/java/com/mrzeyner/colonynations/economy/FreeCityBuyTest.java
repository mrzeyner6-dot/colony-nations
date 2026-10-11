package com.mrzeyner.colonynations.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FreeCityBuyTest {
    @Test
    void priceIsBasePlusAdultsAndReturnCostsMore() {
        assertEquals(700L, FreeCityBuy.price(500, 50, 4, false, 1.5));
        assertEquals(500L, FreeCityBuy.price(500, 50, 0, false, 1.5));
        assertEquals(1050L, FreeCityBuy.price(500, 50, 4, true, 1.5));
        assertEquals(825L, FreeCityBuy.price(500, 50, 1, true, 1.5));
    }

    @Test
    void negativeInputsDoNotLowerThePrice() {
        assertEquals(500L, FreeCityBuy.price(500, -10, -3, false, 1.5));
        assertEquals(0L, FreeCityBuy.price(10, 0, 1, true, 0));
    }

    @Test
    void hugeCitizenBillStaysAtTheLongCeiling() {
        assertEquals(Long.MAX_VALUE, FreeCityBuy.price(Long.MAX_VALUE, 50, 1, false, 1.5));
        assertEquals(Long.MAX_VALUE, FreeCityBuy.price(Long.MAX_VALUE - 10, 50, 1, true, 3));
    }

    @Test
    void rangeIsInclusiveAndRejectsAnotherDimension() {
        assertTrue(FreeCityBuy.inRange(0, 8));
        assertTrue(FreeCityBuy.inRange(8, 8));
        assertFalse(FreeCityBuy.inRange(9, 8));
        assertFalse(FreeCityBuy.inRange(Integer.MAX_VALUE, 8));
        assertFalse(FreeCityBuy.inRange(1, -1));
    }

    @Test
    void cooldownOpensOnTheDayTheWaitEnds() {
        assertTrue(FreeCityBuy.cooled(null, 10, 3));
        assertTrue(FreeCityBuy.cooled(10L, 12, 0));
        assertFalse(FreeCityBuy.cooled(10L, 12, 3));
        assertTrue(FreeCityBuy.cooled(10L, 13, 3));
        assertEquals(13L, FreeCityBuy.readyDay(10L, 12, 3));
    }

    @Test
    void quotePrefersDistanceThenCooldownThenTreasury() {
        FreeCityBuy.Quote far = FreeCityBuy.quote(500, 50, 0, false, 1.5, 9, 8, 0, 10L, 10, 3);
        assertEquals(FreeCityBuy.TOO_FAR, far.block());
        assertEquals(500L, far.price());

        FreeCityBuy.Quote waiting = FreeCityBuy.quote(500, 50, 0, false, 1.5, 8, 8, 1, 10L, 12, 3);
        assertEquals(FreeCityBuy.COOLDOWN, waiting.block());
        assertEquals(13L, waiting.readyDay());

        FreeCityBuy.Quote poor = FreeCityBuy.quote(500, 50, 2, true, 1.5, 3, 8, 899, null, 4, 3);
        assertEquals(FreeCityBuy.POOR, poor.block());
        assertEquals(900L, poor.price());

        FreeCityBuy.Quote ok = FreeCityBuy.quote(500, 50, 2, true, 1.5, 3, 8, 900, null, 4, 3);
        assertEquals(FreeCityBuy.OK, ok.block());
    }
}
