package com.mrzeyner.colonynations.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class TradeTermsTest {
    private static final UUID BUYER = UUID.fromString("00000000-0000-0000-0000-0000000000b1");

    @Test
    void tariffRoundsUpAndEmbargoBlocksTheSale() {
        assertEquals(100L, TradeTerms.quote(100, 0, false, false, 200).price());
        assertEquals(110L, TradeTerms.quote(100, 10, false, false, 200).price());
        assertEquals(5L, TradeTerms.quote(3, 50, false, false, 200).price());
        assertEquals(2L, TradeTerms.quote(1, 1, false, false, 200).price());
        TradeTerms.Quote blocked = TradeTerms.quote(40, 25, true, false, 200);
        assertFalse(blocked.allowed());
        assertEquals(300L, TradeTerms.quote(100, 999, false, false, 200).price());
    }

    @Test
    void privateBuyerPaysTheBasePriceDespiteEmbargo() {
        TradeTerms.Quote quote = TradeTerms.quote(40, 200, true, true, 200);
        assertTrue(quote.allowed());
        assertEquals(40L, quote.price());
        assertFalse(TradeTerms.quote(0, 10, false, false, 200).allowed());
    }

    @Test
    void missingRowUsesTheDefaultAndAToggleCanClearIt() {
        TradePolicy policy = TradePolicy.empty().withDefault(10, 200).bump(BUYER, 5, 200);
        assertEquals(15, policy.duty(BUYER).tariff());
        TradePolicy embargoed = policy.toggleEmbargo(BUYER, 200);
        assertTrue(embargoed.duty(BUYER).embargo());
        TradePolicy cleared = embargoed.toggleEmbargo(BUYER, 200).bump(BUYER, -5, 200);
        assertFalse(cleared.duties().containsKey(BUYER));
        assertEquals(10, cleared.duty(BUYER).tariff());
        assertFalse(cleared.duty(BUYER).embargo());
    }

    @Test
    void salesOnTheNewDayKeepTheUnreportedDay() {
        TradeSales.Book day = TradeSales.record(TradeSales.Book.empty(), 4, 2, 80);
        TradeSales.Book next = TradeSales.record(day, 5, 1, 30);
        TradeSales.Flush flush = TradeSales.flush(next, 5);
        assertEquals(2, flush.report().packs());
        assertEquals(80L, flush.report().spurs());
        assertEquals(1, flush.book().open().packs());
        TradeSales.Flush later = TradeSales.flush(flush.book(), 6);
        assertEquals(1, later.report().packs());
        assertEquals(30L, later.report().spurs());
        assertTrue(later.book().isEmpty());
    }

    @Test
    void coinsWithoutAPackDoNotAnnounce() {
        TradeSales.Book coins = TradeSales.record(TradeSales.Book.empty(), 3, 0, 15);
        TradeSales.Flush flush = TradeSales.flush(coins, 4);
        assertNull(flush.report());
        assertTrue(flush.book().isEmpty());
    }
}
